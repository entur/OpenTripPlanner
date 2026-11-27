package org.opentripplanner.perfrig.agent;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import rigops.Ops;

/**
 * perf-rig: a Java agent that counts the bytecode instructions executed, per method, in the
 * classes whose names start with one of the given prefixes ({@code -javaagent:ops-agent.jar=
 * org/opentripplanner/,java/util/}). The count is a measure of the work done that does not depend
 * on the machine, the operating system or the JIT compiler: the same code with the same input
 * gives the same count.
 * <p>
 * Each basic block adds its number of instructions to the counter of its method, see {@link Ops}.
 * Methods the JIT compiler may replace with its own code (intrinsics) are not counted: their count
 * would depend on how the method was compiled.
 */
public final class OpsAgent implements ClassFileTransformer {

  private static final ClassDesc OPS = ClassDesc.of("rigops.Ops");
  private static final ClassDesc LONG_ARRAY = ConstantDescs.CD_long.arrayType();
  private static final String INTRINSIC = "Ljdk/internal/vm/annotation/IntrinsicCandidate;";
  /** The rig itself and this agent are not counted. */
  private static final List<String> EXCLUDED = List.of("org/opentripplanner/perfrig/", "rigops/");

  private final String[] prefixes;
  private final ClassFile classFile;
  private final List<String> failed = new ArrayList<>();
  private int classes;

  private OpsAgent(String[] prefixes) {
    this.prefixes = prefixes;
    // Class files are read as resources, so resolving a class hierarchy never loads a class
    var resolver = ClassHierarchyResolver.ofResourceParsing(ClassLoader.getSystemClassLoader());
    this.classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver.cached()));
  }

  public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
    var agent = new OpsAgent(arguments.split(","));
    // Run the transformer once before it is in use, so the classes it needs are loaded
    try (var self = OpsAgent.class.getResourceAsStream("OpsAgent.class")) {
      agent.count("org/opentripplanner/perfrig/agent/OpsAgent", self.readAllBytes());
    }
    instrumentation.addTransformer(agent, true);
    int retransformed = 0;
    for (Class<?> type : instrumentation.getAllLoadedClasses()) {
      if (agent.includes(type.getName().replace('.', '/')) && instrumentation.isModifiableClass(type)) {
        try {
          instrumentation.retransformClasses(type);
          retransformed++;
        } catch (Throwable e) {
          agent.failed(type.getName(), e);
        }
      }
    }
    System.err.println(
      "[ops-agent] counting " + arguments + ", " + retransformed + " loaded classes instrumented"
    );
    Runtime.getRuntime().addShutdownHook(new Thread(agent::report));
  }

  @Override
  public byte[] transform(
    Module module,
    ClassLoader loader,
    String name,
    Class<?> classBeingRedefined,
    ProtectionDomain protectionDomain,
    byte[] bytes
  ) {
    if (name == null || !includes(name)) {
      return null;
    }
    try {
      return count(name, bytes);
    } catch (Throwable e) {
      failed(name, e);
      return null;
    }
  }

  private boolean includes(String name) {
    for (String excluded : EXCLUDED) {
      if (name.startsWith(excluded)) {
        return false;
      }
    }
    for (String prefix : prefixes) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** The class with a counter update in every basic block of every method. */
  private byte[] count(String name, byte[] bytes) {
    byte[] result = classFile.transformClass(classFile.parse(bytes), (builder, element) -> {
      if (
        element instanceof MethodModel method && method.code().isPresent() && !isIntrinsic(method)
      ) {
        var id = name + "." + method.methodName().stringValue() + method.methodType().stringValue();
        var lines = method
          .code()
          .get()
          .elementStream()
          .filter(LineNumber.class::isInstance)
          .mapToInt(it -> ((LineNumber) it).line())
          .summaryStatistics();
        int index = lines.getCount() == 0
          ? Ops.register(id, 0, 0)
          : Ops.register(id, lines.getMin(), lines.getMax());
        if (index >= 0) {
          builder.transformMethod(method, MethodTransform.transformingCode(new CountBlocks(index)));
          return;
        }
      }
      builder.with(element);
    });
    synchronized (this) {
      classes++;
    }
    return result;
  }

  private static boolean isIntrinsic(MethodModel method) {
    return method
      .findAttribute(Attributes.runtimeVisibleAnnotations())
      .map(a -> a.annotations().stream().anyMatch(it -> it.className().equalsString(INTRINSIC)))
      .orElse(false);
  }

  private synchronized void failed(String name, Throwable e) {
    failed.add(name + " (" + e + ")");
  }

  private synchronized void report() {
    System.err.println(
      "[ops-agent] " + classes + " classes and " + Ops.size() + " methods instrumented, " +
        failed.size() + " classes failed and are not counted" +
        (failed.isEmpty() ? "" : ": " + failed.subList(0, Math.min(20, failed.size())))
    );
  }

  /**
   * Adds the number of instructions of each basic block to the counter of the method, at the end
   * of the block: before the instruction that leaves it, or before the label that starts the next.
   */
  private static final class CountBlocks implements CodeTransform {

    private final int method;
    private int pending = 0;

    CountBlocks(int method) {
      this.method = method;
    }

    @Override
    public void accept(CodeBuilder code, CodeElement element) {
      if (element instanceof LabelTarget) {
        flush(code);
      } else if (element instanceof Instruction instruction) {
        pending++;
        if (leavesBlock(instruction)) {
          flush(code);
        }
      }
      code.with(element);
    }

    private static boolean leavesBlock(Instruction instruction) {
      return (
        instruction instanceof BranchInstruction ||
        instruction instanceof TableSwitchInstruction ||
        instruction instanceof LookupSwitchInstruction ||
        instruction instanceof ReturnInstruction ||
        instruction instanceof ThrowInstruction ||
        instruction instanceof DiscontinuedInstruction
      );
    }

    /** COUNTS[method] += pending. Leaves the operand stack as it was. */
    private void flush(CodeBuilder code) {
      if (pending == 0) {
        return;
      }
      code
        .getstatic(OPS, "COUNTS", LONG_ARRAY)
        .loadConstant(method)
        .dup2()
        .laload()
        .loadConstant((long) pending)
        .ladd()
        .lastore();
      pending = 0;
    }
  }
}
