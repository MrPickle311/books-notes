package com.example.bpmn.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.bpmn.model.Participant;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.lang.model.element.Modifier;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class BPMNCodeGeneratorMojo extends AbstractMojo {

    private static final Pattern SERVICE_TASK_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^\\.]+)\\.([^\\(]+)\\(\\)\\}");

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/bpmn")
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/bpmn")
    private File outputDirectory;

    @Parameter(defaultValue = "com.example.generated")
    private String packageName;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("BPMN Code Generator is starting...");
        getLog().info("Source directory: " + sourceDirectory.getAbsolutePath());
        getLog().info("Output directory: " + outputDirectory.getAbsolutePath());
        getLog().info("Package name: " + packageName);

        if (!sourceDirectory.exists()) {
            getLog().warn("Source directory does not exist, skipping code generation.");
            return;
        }

        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(path -> path.toString().endsWith(".bpmn") || path.toString().endsWith(".bpmn20.xml"))
                    .forEach(this::processBpmnFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan for BPMN files.", e);
        }

        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().info("Added generated sources to compile root: " + outputDirectory.getAbsolutePath());
    }

    private void processBpmnFile(Path bpmnFile) {
        getLog().info("Processing BPMN file: " + bpmnFile);
        BpmnModel model = readModel(bpmnFile);
        if (model != null) {
            model.getProcesses().forEach(process -> {
                if (process.isExecutable()) {
                    String processName = getProcessName(model, process);
                    getLog().info("Found process: " + process.getId() + " - " + processName);
                    generateProcessService(process, processName);

                    process.getFlowElements().stream()
                            .filter(flowElement -> flowElement instanceof ServiceTask)
                            .forEach(flowElement -> generateServiceInterfaces((ServiceTask) flowElement));
                }
            });
        }
    }

    private BpmnModel readModel(Path bpmnFile) {
        try (InputStream is = new FileInputStream(bpmnFile.toFile())) {
            XMLInputFactory xif = XMLInputFactory.newInstance();
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader xtr = xif.createXMLStreamReader(is);
            return new BpmnXMLConverter().convertToBpmnModel(xtr);
        } catch (IOException | XMLStreamException e) {
            getLog().error("Failed to read BPMN file: " + bpmnFile, e);
            return null;
        }
    }

    private String getProcessName(BpmnModel model, Process process) {
        if (process.getName() != null && !process.getName().isEmpty()) {
            return process.getName();
        }
        return model.getParticipants().stream()
                .filter(participant -> participant.getProcessRef().equals(process.getId()))
                .map(Participant::getName)
                .findFirst()
                .orElse(process.getId());
    }

    private void generateServiceInterfaces(ServiceTask serviceTask) {
        if (serviceTask.getImplementation() == null || !serviceTask.getImplementationType().equals("expression")) {
            return;
        }

        Matcher matcher = SERVICE_TASK_EXPRESSION_PATTERN.matcher(serviceTask.getImplementation());
        if (matcher.matches()) {
            String beanName = matcher.group(1);
            String methodName = matcher.group(2);
            String interfaceName = toInterfaceName(beanName);

            MethodSpec methodSpec = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build();

            TypeSpec serviceInterface = TypeSpec.interfaceBuilder(interfaceName)
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(methodSpec)
                    .build();

            JavaFile javaFile = JavaFile.builder(packageName, serviceInterface)
                    .build();
            try {
                javaFile.writeTo(outputDirectory);
            } catch (IOException e) {
                getLog().error("Failed to write generated interface: " + interfaceName, e);
            }
        }
    }

    private void generateProcessService(Process process, String processName) {
        String className = toPascalCase(processName) + "ProcessService";
        getLog().info("Generating process service: " + className);

        ClassName runtimeServiceClass = ClassName.get("org.activiti.engine", "RuntimeService");
        FieldSpec runtimeServiceField = FieldSpec.builder(runtimeServiceClass, "runtimeService", Modifier.PRIVATE)
                .addAnnotation(ClassName.get("org.springframework.beans.factory.annotation", "Autowired"))
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .build();

        ParameterSpec variablesParam = ParameterSpec.builder(ParameterizedTypeName.get(Map.class, String.class, Object.class), "variables")
                .build();

        MethodSpec startProcessMethod = MethodSpec.methodBuilder("startProcess")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(variablesParam)
                .returns(String.class)
                .addStatement("return runtimeService.startProcessInstanceByKey($S, $N).getId()", process.getId(), variablesParam)
                .build();

        TypeSpec processService = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Service"))
                .addField(runtimeServiceField)
                .addMethod(constructor)
                .addMethod(startProcessMethod)
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, processService)
                .build();
        try {
            javaFile.writeTo(outputDirectory);
        } catch (IOException e) {
            getLog().error("Failed to write generated service: " + className, e);
        }
    }

    private String toPascalCase(String original) {
        if (original == null || original.isEmpty()) {
            return "";
        }
        StringBuilder pascalCase = new StringBuilder();
        boolean toUpperCase = true;
        for (char c : original.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '_') {
                toUpperCase = true;
            } else {
                if (toUpperCase) {
                    pascalCase.append(Character.toUpperCase(c));
                    toUpperCase = false;
                } else {
                    pascalCase.append(Character.toLowerCase(c));
                }
            }
        }
        return pascalCase.toString();
    }

    private String toInterfaceName(String beanName) {
        if (beanName == null || beanName.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(beanName.charAt(0)) + beanName.substring(1);
    }
} 