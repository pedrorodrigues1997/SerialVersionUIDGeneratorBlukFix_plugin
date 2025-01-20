package com.pedrao.serialversionuidgenerator;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

public class SerializableWithoutSerialVersionUIDInspectionss extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public ProblemDescriptor @Nullable [] checkClass(
            @NotNull PsiClass psiClass,
            @NotNull InspectionManager manager,
            boolean isOnTheFly) {
        // Skip interfaces enums and classes that do not implement Serializable
        if(psiClass == null){
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        if (psiClass.isInterface() || psiClass.isEnum() || !implementsSerializable(psiClass)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        boolean hasSerialUID = Arrays.stream(psiClass.getFields())
                .anyMatch(field -> "serialVersionUID".equals(field.getName()));


        if (!hasSerialUID) {
            PsiElement nameIdentifier = psiClass.getNameIdentifier();
            if (nameIdentifier == null) {
                return null;
            }

            return new ProblemDescriptor[]{
                    manager.createProblemDescriptor(
                            nameIdentifier,
                            "Serializable class is missing serialVersionUID",
                            new SerializableQuickFix(),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            isOnTheFly
                    )
            };
        }

        return null;
    }


    private boolean implementsSerializable(PsiClass psiClass) {
        if (psiClass == null){
            return false;
        }
        return hasSerializableInterface(psiClass) ||
                implementsSerializable(psiClass.getSuperClass()) ||
                Arrays.stream(psiClass.getInterfaces())
                        .anyMatch(this::isInterfaceSerializable);
    }


    // This checks if the interface (or its parent interfaces) directly extends Serializable
    private boolean isInterfaceSerializable(PsiClass psiInterface) {
        PsiReferenceList extendsList = psiInterface.getExtendsList();
        if (extendsList != null) {
            for (PsiJavaCodeReferenceElement interfaceRef : extendsList.getReferenceElements()) {
                PsiClass resolvedInterface = (PsiClass) interfaceRef.resolve();
                if (resolvedInterface != null && "java.io.Serializable".equals(resolvedInterface.getQualifiedName())) {
                    return true; // This interface directly extends Serializable
                }

                // Recursively check if any parent interface extends Serializable
                if (isInterfaceSerializable(resolvedInterface)) {
                    return true;
                }
            }
        }
        return false;
    }


    private boolean hasSerializableInterface(PsiClass psiClass) {
        PsiReferenceList implementsList = psiClass.getImplementsList();
        if (implementsList == null) {
            return false;
        }

        return Arrays.stream(implementsList.getReferencedTypes())
                .anyMatch(type -> "java.io.Serializable".equals(type.getCanonicalText()));
    }


    private static class SerializableQuickFix implements LocalQuickFix {

        @Override
        public @NotNull String getFamilyName() {
            return "Add serialVersionUID field";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

            PsiElement element = descriptor.getPsiElement();
            if (!(element.getParent() instanceof PsiClass)) {
                return;
            }
            PsiClass psiClass = (PsiClass) element.getParent();


            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            long uniqueSerialVersionUID = UUID.randomUUID().getMostSignificantBits();
            PsiField serialVersionUIDField = factory.createFieldFromText(
                    "private static final long serialVersionUID = " + uniqueSerialVersionUID + "L;",
                    psiClass
            );

            psiClass.add(serialVersionUIDField);
        }
    }
}
