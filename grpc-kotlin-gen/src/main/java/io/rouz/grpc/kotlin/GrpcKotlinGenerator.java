/*-
 * -\-\-
 * grpc-kotlin-gen
 * --
 * Copyright (C) 2016 - 2018 rouz.io
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see https://opensource.org/licenses/BSD-3-Clause
 */

package io.rouz.grpc.kotlin;

import static com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import static com.google.protobuf.DescriptorProtos.FileOptions;
import static com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import static com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import static com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;
import com.salesforce.jprotoc.ProtocPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GrpcKotlinGenerator extends Generator {

  private static final int METHOD_NUMBER_OF_PATHS = 4;
  private static final String CLASS_SUFFIX = "GrpcKt";
  private static final String SERVICE_JAVA_DOC_PREFIX = "    ";
  private static final String METHOD_JAVA_DOC_PREFIX = "        ";

  public static void main(String[] args) {
    ProtocPlugin.generate(new GrpcKotlinGenerator());
  }

  @Override
  public Stream<CodeGeneratorResponse.File> generate(CodeGeneratorRequest request)
      throws GeneratorException {
    final ProtoTypeMap typeMap = ProtoTypeMap.of(request.getProtoFileList());

    List<FileDescriptorProto> protosToGenerate = request.getProtoFileList().stream()
        .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
        .collect(Collectors.toList());

    List<Context> services = findServices(protosToGenerate, typeMap);
    List<PluginProtos.CodeGeneratorResponse.File> files = generateFiles(services);
    return files.stream();

  }

  private List<Context> findServices(List<FileDescriptorProto> protos, ProtoTypeMap typeMap) {
    List<Context> contexts = new ArrayList<>();

    protos.forEach(fileProto -> {
      List<Location> locations = fileProto.getSourceCodeInfo().getLocationList();
      locations.stream()
          .filter(location -> location.getPathCount() == 2
              && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER)
          .forEach(location -> {
            int serviceNumber = location.getPath(1);
            Context context = context(
                fileProto.getService(serviceNumber), typeMap, locations, serviceNumber);
            context.javaDoc = getJavaDoc(getComments(location), SERVICE_JAVA_DOC_PREFIX);
            context.protoName = fileProto.getName();
            context.packageName = extractPackageName(fileProto);
            contexts.add(context);
          });
    });

    return contexts;
  }

  private String extractPackageName(FileDescriptorProto proto) {
    FileOptions options = proto.getOptions();
    if (options != null) {
      String javaPackage = options.getJavaPackage();
      if (!Strings.isNullOrEmpty(javaPackage)) {
        return javaPackage;
      }
    }

    return Strings.nullToEmpty(proto.getPackage());
  }

  private Context context(
      ServiceDescriptorProto serviceProto,
      ProtoTypeMap protoTypeMap,
      List<Location> locations,
      int serviceNumber) {

    Context context = new Context();
    context.fileName = serviceProto.getName() + CLASS_SUFFIX + ".kt";
    context.className = serviceProto.getName() + CLASS_SUFFIX;
    context.serviceName = serviceProto.getName();
    context.deprecated = serviceProto.getOptions() != null
        && serviceProto.getOptions().getDeprecated();

    locations.stream()
        .filter(location -> location.getPathCount() == METHOD_NUMBER_OF_PATHS
                            && location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER
                            && location.getPath(1) == serviceNumber
                            && location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER)
        .forEach(location -> {
          int methodNumber = location.getPath(METHOD_NUMBER_OF_PATHS - 1);
          MethodContext methodContext = methodContext(
              serviceProto.getMethod(methodNumber), protoTypeMap);
          methodContext.methodNumber = methodNumber;
          methodContext.javaDoc = getJavaDoc(getComments(location), METHOD_JAVA_DOC_PREFIX);
          context.methods.add(methodContext);
        });
    return context;
  }

  private MethodContext methodContext(MethodDescriptorProto methodProto, ProtoTypeMap typeMap) {
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = lowerCaseFirst(methodProto.getName());
    methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
    methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
    methodContext.deprecated = methodProto.getOptions() != null
        && methodProto.getOptions().getDeprecated();
    methodContext.isManyInput = methodProto.getClientStreaming();
    methodContext.isManyOutput = methodProto.getServerStreaming();
    if (!methodProto.getClientStreaming() && !methodProto.getServerStreaming()) {
      methodContext.grpcCallsMethodName = "asyncUnaryCall";
    }
    if (!methodProto.getClientStreaming() && methodProto.getServerStreaming()) {
      methodContext.grpcCallsMethodName = "asyncServerStreamingCall";
    }
    if (methodProto.getClientStreaming() && !methodProto.getServerStreaming()) {
      methodContext.grpcCallsMethodName = "asyncClientStreamingCall";
    }
    if (methodProto.getClientStreaming() && methodProto.getServerStreaming()) {
      methodContext.grpcCallsMethodName = "asyncBidiStreamingCall";
    }
    return methodContext;
  }

  private String lowerCaseFirst(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  private List<PluginProtos.CodeGeneratorResponse.File> generateFiles(List<Context> services) {
    return services.stream()
        .map(this::buildFile)
        .collect(Collectors.toList());
  }

  private PluginProtos.CodeGeneratorResponse.File buildFile(Context context) {
    String content = applyTemplate("KtStub.mustache", context);
    return PluginProtos.CodeGeneratorResponse.File
        .newBuilder()
        .setName(absoluteFileName(context))
        .setContent(content)
        .build();
  }

  private String absoluteFileName(Context ctx) {
    String dir = ctx.packageName.replace('.', '/');
    if (Strings.isNullOrEmpty(dir)) {
      return ctx.fileName;
    } else {
      return dir + "/" + ctx.fileName;
    }
  }

  private String getComments(Location location) {
    return location.getLeadingComments().isEmpty()
        ? location.getTrailingComments()
        : location.getLeadingComments();
  }

  private String getJavaDoc(String comments, String prefix) {
    if (!comments.isEmpty()) {
      StringBuilder builder = new StringBuilder("/**\n")
          .append(prefix).append(" * <pre>\n");
      Arrays.stream(HtmlEscapers.htmlEscaper().escape(comments).split("\n"))
          .forEach(line -> builder.append(prefix).append(" * ").append(line).append("\n"));
      builder
          .append(prefix).append(" * <pre>\n")
          .append(prefix).append(" */");
      return builder.toString();
    }
    return null;
  }

  /**
   * Template class for proto Service objects.
   */
  private class Context {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 8 LINES
    public String fileName;
    public String protoName;
    public String packageName;
    public String className;
    public String serviceName;
    public boolean deprecated;
    public String javaDoc;
    public List<MethodContext> methods = new ArrayList<>();
  }

  /**
   * Template class for proto RPC objects.
   */
  private class MethodContext {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 10 LINES
    public String methodName;
    public String inputType;
    public String outputType;
    public boolean deprecated;
    public boolean isManyInput;
    public boolean isManyOutput;
    public String grpcCallsMethodName;
    public int methodNumber;
    public String javaDoc;

    // This method mimics the upper-casing method ogf gRPC to ensure compatibility
    // See https://github.com/grpc/grpc-java/blob/v1.8.0/compiler/src/java_plugin/cpp/java_generator.cpp#L58
    public String methodNameUpperUnderscore() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < methodName.length(); i++) {
        char c = methodName.charAt(i);
        s.append(Character.toUpperCase(c));
        if ((i < methodName.length() - 1)
            && Character.isLowerCase(c)
            && Character.isUpperCase(methodName.charAt(i + 1))) {
          s.append('_');
        }
      }
      return s.toString();
    }

    public String methodNamePascalCase() {
      return String.valueOf(Character.toUpperCase(methodName.charAt(0))) + methodName.substring(1);
    }
  }
}
