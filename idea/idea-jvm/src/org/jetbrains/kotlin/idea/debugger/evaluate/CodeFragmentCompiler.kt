/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension.Context as InCo
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.data.KtClassLikeInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyPackageDescriptor
import org.jetbrains.kotlin.resolve.scopes.ChainedMemberScope
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.Printer

object CodeFragmentCompiler {
    fun compile(codeFragment: KtCodeFragment, bindingContext: BindingContext, moduleDescriptor: ModuleDescriptor): List<OutputFile> {
        val project = codeFragment.project
        val resolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacade(listOf(codeFragment))
        val resolveSession = resolutionFacade.getFrontendService(ResolveSession::class.java)
        val moduleDescriptorWrapper = EvaluatorModuleDescriptor(codeFragment, moduleDescriptor, resolveSession)

        val defaultReturnType = moduleDescriptor.builtIns.unitType
        val returnType = if (codeFragment is KtExpressionCodeFragment) {
            val typeInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, codeFragment.getContentElement()]
            typeInfo?.type ?: defaultReturnType
        } else {
            defaultReturnType
        }

        val parameterInfo = CodeFragmentParameterAnalyzer(bindingContext).analyze(codeFragment)
        val (classDescriptor, methodDescriptor) = createDescriptorsForCodeFragment(
            codeFragment, Name.identifier(GENERATED_CLASS_NAME), Name.identifier(GENERATED_FUNCTION_NAME),
            parameterInfo, returnType, moduleDescriptorWrapper.packageFragmentForEvaluator
        )

        val codegenInfo = CodeFragmentCodegenInfo(classDescriptor, methodDescriptor) { expr, _ ->
            referenceInterceptor(parameterInfo, expr)
        }

        codeFragment.putUserData(CodeFragmentCodegen.INFO_USERDATA_KEY, codegenInfo)

        val compilerConfiguration = CompilerConfiguration()
        compilerConfiguration.languageVersionSettings = codeFragment.languageVersionSettings

        val generationState = GenerationState.Builder(
            project,
            ClassBuilderFactories.BINARIES,
            moduleDescriptorWrapper,
            bindingContext,
            listOf(codeFragment),
            compilerConfiguration
        ).build()

        KotlinCodegenFacade.compileCorrectFiles(generationState, CompilationErrorHandler.THROW_EXCEPTION)
//        val text = generationState.factory.asList().filterClassFiles().map { it.asText() }
        return generationState.factory.asList().filterClassFiles()
    }

    private fun InCo.referenceInterceptor(
        parameterInfo: CodeFragmentParameterInfo,
        expression: KtExpression
    ): StackValue? {
        val parameter = parameterInfo.mappings[expression] ?: return null
        val asmType = typeMapper.mapType(parameter.type)
        return StackValue.local(parameter.index, asmType, parameter.type)
    }

    private fun createDescriptorsForCodeFragment(
        declaration: KtCodeFragment,
        className: Name,
        methodName: Name,
        parameterInfo: CodeFragmentParameterInfo,
        returnType: KotlinType,
        packageFragmentDescriptor: PackageFragmentDescriptor
    ): Pair<ClassDescriptor, FunctionDescriptor> {
        val classDescriptor = ClassDescriptorImpl(
            packageFragmentDescriptor, className, Modality.FINAL, ClassKind.OBJECT,
            emptyList(),
            KotlinSourceElement(declaration),
            false,
            LockBasedStorageManager.NO_LOCKS
        )

        val methodDescriptor = SimpleFunctionDescriptorImpl.create(
            classDescriptor, Annotations.EMPTY, methodName,
            CallableMemberDescriptor.Kind.SYNTHESIZED, classDescriptor.source
        )

        val parameters = parameterInfo.parameters.mapIndexed { index, parameter ->
            ValueParameterDescriptorImpl(
                methodDescriptor, null, index, Annotations.EMPTY, Name.identifier("p$index"),
                parameter.type, false, false, false, null, SourceElement.NO_SOURCE
            )
        }

        methodDescriptor.initialize(
            null, classDescriptor.thisAsReceiverParameter, emptyList(),
            parameters, returnType, Modality.FINAL, Visibilities.PUBLIC
        )

        val memberScope = EvaluatorMemberScopeForMethod(methodDescriptor)

        val constructor = ClassConstructorDescriptorImpl.create(classDescriptor, Annotations.EMPTY, true, classDescriptor.source)
        classDescriptor.initialize(memberScope, setOf(constructor), constructor)

        return Pair(classDescriptor, methodDescriptor)
    }
}

private class EvaluatorMemberScopeForMethod(private val methodDescriptor: SimpleFunctionDescriptor) : MemberScopeImpl() {
    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
        return if (name == methodDescriptor.name) {
            listOf(methodDescriptor)
        } else {
            emptyList()
        }
    }

    override fun getContributedDescriptors(
        kindFilter: DescriptorKindFilter,
        nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> {
        return if (kindFilter.accepts(methodDescriptor) && nameFilter(methodDescriptor.name)) {
            listOf(methodDescriptor)
        } else {
            emptyList()
        }
    }

    override fun getFunctionNames() = setOf(methodDescriptor.name)

    override fun printScopeStructure(p: Printer) {
        p.println(this::class.java.simpleName)
    }
}

private class EvaluatorModuleDescriptor(
    val codeFragment: KtCodeFragment,
    val moduleDescriptor: ModuleDescriptor,
    resolveSession: ResolveSession
) : ModuleDescriptor by moduleDescriptor {
    private val declarationProvider = object : PackageMemberDeclarationProvider {
        override fun getPackageFiles() = listOf(codeFragment)
        override fun containsFile(file: KtFile) = file == codeFragment

        override fun getDeclarationNames() = emptySet<Name>()
        override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean) = emptyList<KtDeclaration>()
        override fun getClassOrObjectDeclarations(name: Name) = emptyList<KtClassLikeInfo>()
        override fun getAllDeclaredSubPackages(nameFilter: (Name) -> Boolean) = emptyList<FqName>()
        override fun getFunctionDeclarations(name: Name) = emptyList<KtNamedFunction>()
        override fun getPropertyDeclarations(name: Name) = emptyList<KtProperty>()
        override fun getTypeAliasDeclarations(name: Name) = emptyList<KtTypeAlias>()
        override fun getDestructuringDeclarationsEntries(name: Name) = emptyList<KtDestructuringDeclarationEntry>()
    }

    val packageFragmentForEvaluator = LazyPackageDescriptor(this, FqName.ROOT, resolveSession, declarationProvider)

    override fun getPackage(fqName: FqName): PackageViewDescriptor {
        val originalPackageDescriptor = moduleDescriptor.getPackage(fqName)
        if (fqName != FqName.ROOT) {
            return originalPackageDescriptor
        }

        return object : DeclarationDescriptorImpl(Annotations.EMPTY, fqName.shortNameOrSpecial()), PackageViewDescriptor {
            override fun getContainingDeclaration() = originalPackageDescriptor.containingDeclaration

            override val fqName get() = originalPackageDescriptor.fqName
            override val module get() = this@EvaluatorModuleDescriptor

            override val memberScope by lazy {
                if (fragments.isEmpty()) {
                    MemberScope.Empty
                } else {
                    val scopes = fragments.map { it.getMemberScope() } + SubpackagesScope(module, fqName)
                    ChainedMemberScope("package view scope for $fqName in ${module.name}", scopes)
                }
            }

            override val fragments by lazy { originalPackageDescriptor.fragments + packageFragmentForEvaluator }

            override fun <R, D> accept(visitor: DeclarationDescriptorVisitor<R, D>, data: D): R {
                return visitor.visitPackageViewDescriptor(this, data)
            }
        }
    }
}