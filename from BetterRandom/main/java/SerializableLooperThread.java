package io.github.pr0methean.betterrandom.util;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * <p>Thread that loops a given task until interrupted (or until JVM shutdown, if it {@link
 * #isDaemon() is a daemon thread}), with the iterations being transactional. Because of these
 * constraints, it can be serialized and cloned. Subclasses must override {@link #iterate()} if
 * instantiated without a target {@link Runnable}; the only reason this class is concrete is that
 * temporary instances are needed during deserialization.</p> <p> Subclasses should override the
 * {@link #readResolveConstructorWrapper()} method to ensure they are deserialized as a subclass
 * instance. </p> <p>{@link #iterate()}'s body should be reasonably short, since it will block
 * serialization and cloning that would otherwise catch it in mid-iteration. </p><p> Thread state
 * that WILL be restored includes that retrievable by: </p> <ul> <li>{@link #getName()}</li>
 * <li>{@link #getPriority()}</li> <li>{@link #getState()} == {@link State#NEW}</li> <li>{@link
 * #getState()} == {@link State#TERMINATED}</li> <li>{@link #isInterrupted()}</li> <li>{@link
 * #isDaemon()}</li> </ul><p> Thread state that will be restored ONLY if its values are {@link
 * Serializable} includes that retrievable by: </p><ul> <li>{@link #getThreadGroup()}</li>
 * <li>{@link #getUncaughtExceptionHandler()}</li> <li>{@link #getContextClassLoader()}</li>
 * </ul><p> Thread state that will NEVER be restored includes: </p><ul> <li>Program counter, call
 * stack, and local variables. Serialization will block until it can happen between iterations of
 * {@link #iterate()}.</li> <li>Suspended status (see {@link Thread#suspend()}).</li> <li>{@link
 * #getState()} == {@link State#TIMED_WAITING}</li> <li>{@link #getState()} == {@link
 * State#WAITING}</li> <li>{@link #getState()} == {@link State#BLOCKED}</li> <li>{@link
 * #getId()}</li> <li>{@link #holdsLock(Object)}</li> </ul>
 * @author Chris Hennick
 */
@SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
public class SerializableLooperThread extends LooperThread implements Serializable, Cloneable {

  private static final long serialVersionUID = -4387051967625864310L;
  /**
   * The preferred stack size for this thread, in bytes, if it was specified during construction; 0
   * otherwise. Held for serialization purposes.
   */
  protected final long stackSize;
  /**
   * The name of this thread, if it has a non-default name. Held for serialization purposes.
   */
  @Nullable protected String name = null;
  /**
   * The {@link ThreadGroup} this thread belongs to, if any. Held for serialization purposes.
   */
  @Nullable protected ThreadGroup serialGroup;
  protected boolean interrupted = false;
  protected boolean daemon = false;
  protected int priority = Thread.NORM_PRIORITY;
  protected State state = State.NEW;
  @Nullable protected ClassLoader contextClassLoader = null;
  @SuppressWarnings("InstanceVariableMayNotBeInitializedByReadObject") private transient boolean
      alreadyTerminatedWhenDeserialized = false;
  @Nullable private Runnable serialTarget;
  @Nullable private UncaughtExceptionHandler serialUncaughtExceptionHandler;

  /**
   * Constructs a LooperThread with all properties as defaults. Protected because it does not set a
   * target, and thus should only be used in subclasses that override {@link #iterate()}.
   */
  protected SerializableLooperThread() {
    super();
    stackSize = 0;
  }

  /**
   * Constructs a LooperThread with the given target. {@code target} should only be null if called
   * from a subclass that overrides {@link #iterate()}.
   * @param target If not null, the target this thread will run in {@link #iterate()}.
   */
  @SuppressWarnings("argument.type.incompatible") @EntryPoint public SerializableLooperThread(
      @Nullable final Runnable target) {
    super(target);
    stackSize = 0;
  }

  /**
   * Constructs a LooperThread that belongs to the given {@link ThreadGroup} and has the given
   * target. {@code target} should only be null if called from a subclass that overrides {@link
   * #iterate()}.
   * @param group The ThreadGroup this thread will belong to.
   * @param target If not null, the target this thread will run in {@link #iterate()}.
   */
  @SuppressWarnings("argument.type.incompatible") @EntryPoint public SerializableLooperThread(
      final ThreadGroup group, @Nullable final Runnable target) {
    super(group, target);
    stackSize = 0;
  }

  /**
   * Constructs a LooperThread with the given name. Protected because it does not set a target, and
   * thus should only be used in subclasses that override {@link #iterate()}.
   * @param name the thread name
   */
  @EntryPoint protected SerializableLooperThread(final String name) {
    super(name);
    stackSize = 0;
  }

  /**
   * Constructs a LooperThread with the given name and belonging to the given {@link ThreadGroup}.
   * Protected because it does not set a target, and thus should only be used in subclasses that
   * override {@link #iterate()}.
   * @param group The ThreadGroup this thread will belong to.
   * @param name the thread name
   */
  @EntryPoint protected SerializableLooperThread(final ThreadGroup group, final String name) {
    super(group, name);
    setGroup(group);
    stackSize = 0;
  }

  /**
   * Constructs a LooperThread with the given name and target. {@code target} should only be null if
   * called from a subclass that overrides {@link #iterate()}.
   * @param name the thread name
   * @param target If not null, the target this thread will run in {@link #iterate()}.
   */
  @SuppressWarnings("argument.type.incompatible") @EntryPoint public SerializableLooperThread(
      @Nullable final Runnable target, final String name) {
    super(target, name);
    stackSize = 0;
  }

  /**
   * Constructs a LooperThread with the given name and target, belonging to the given {@link
   * ThreadGroup}. {@code target} should only be null if called from a subclass that overrides
   * {@link #iterate()}.
   * @param group The ThreadGroup this thread will belong to.
   * @param target If not null, the target this thread will run in {@link #iterate()}.
   * @param name the thread name
   */
  @SuppressWarnings("argument.type.incompatible") @EntryPoint public SerializableLooperThread(
      final ThreadGroup group, @Nullable final Runnable target, final String name) {
    super(group, target, name);
    setGroup(group);
    stackSize = 0;
  }

  /**
   * <p>Constructs a LooperThread with the given name and target, belonging to the given {@link
   * ThreadGroup} and having the given preferred stack size. {@code target} should only be null if
   * called from a subclass that overrides {@link #iterate()}.</p>
   * <p>See {@link Thread#Thread(ThreadGroup, Runnable, String, long)} for caveats about
   * specifying the stack size.</p>
   * @param group The ThreadGroup this thread will belong to.
   * @param target If not null, the target this thread will run in {@link #iterate()}.
   * @param name the thread name
   * @param stackSize the desired stack size for the new thread, or zero to indicate that this
   *     parameter is to be ignored.
   */
  @SuppressWarnings("argument.type.incompatible") public SerializableLooperThread(
      final ThreadGroup group, @Nullable final Runnable target, final String name,
      final long stackSize) {
    super(group, target, name, stackSize);
    setGroup(group);
    this.stackSize = stackSize;
  }

  @Nullable private static <T> T serializableOrNull(@Nullable final T object) {
    if (!(object instanceof Serializable)) {
      return null;
    }
    return object;
  }

  private void setGroup(@Nullable final ThreadGroup group) {
    serialGroup = (group instanceof Serializable) ? group : null;
  }

  /**
   * Used only to prepare subclasses before readResolve.
   * @param in The {@link ObjectInputStream} we're being read from.
   * @throws IOException When thrown by {@link ObjectInputStream#defaultReadObject}.
   * @throws ClassNotFoundException When thrown by {@link ObjectInputStream#defaultReadObject}.
   */
  private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    lock = new ReentrantLock();
    endOfIteration = lock.newCondition();
  }

  /**
   * Deserialization uses readResolve rather than {@link #readObject(ObjectInputStream)} alone,
   * because the API of {@link Thread} only lets us set preferred stack size and thread serialGroup
   * during construction and not update them afterwards.
   * @return A LooperThread that will replace this one during deserialization.
   * @throws InvalidObjectException if this LooperThread's serial form is invalid.
   */
  protected Object readResolve() throws InvalidObjectException {
    target = serialTarget;
    if (name == null) {
      name = getName();
    }
    if (target == null) {
      target = new DummyTarget();
    }
    if (serialGroup == null) {
      serialGroup = currentThread().getThreadGroup();
    }
    final SerializableLooperThread t = readResolveConstructorWrapper();
    t.setDaemon(daemon);
    t.setPriority(priority);
    if (serialUncaughtExceptionHandler != null) {
      t.setUncaughtExceptionHandler(serialUncaughtExceptionHandler);
    }
    if (contextClassLoader != null) {
      t.setContextClassLoader(contextClassLoader);
    }
    switch (state) {
      case NEW:
        t.alreadyTerminatedWhenDeserialized = false;
        break;
      case RUNNABLE:
      case BLOCKED:
      case WAITING:
      case TIMED_WAITING:
        t.alreadyTerminatedWhenDeserialized = false;
        t.start();
        break;
      case TERMINATED:
        t.setStopped();
        t.alreadyTerminatedWhenDeserialized = true;
    }
    if (interrupted) {
      t.interrupt();
    }
    return t;
  }

  /**
   * Returns a new instance of this LooperThread's exact class, whose serialGroup is {@link
   * #serialGroup}, whose target is {@link #target}, whose name is {@link #name}, whose preferred
   * stack size per {@link Thread#Thread(ThreadGroup, Runnable, String, long)} is {@link
   * #stackSize}, and that has its subclass fields copied from this one if it does not have a {@link
   * #readResolve()} override that will populate them before deserialization completes. Must be
   * overridden in <em>all</em> subclasses to fulfill this contract.
   * @return the new LooperThread.
   * @throws InvalidObjectException if this LooperThread's serial form is invalid.
   */
  protected SerializableLooperThread readResolveConstructorWrapper() throws InvalidObjectException {
    return new SerializableLooperThread(serialGroup, target, name, stackSize);
  }

  private void setStopped() {
    if (getState() == State.NEW) {
      start();
    }
    interrupt();
    interrupted(); // Clear interrupted flag
  }

  @Override public State getState() {
    return alreadyTerminatedWhenDeserialized ? State.TERMINATED : super.getState();
  }

  @Override public synchronized void start() {
    if (alreadyTerminatedWhenDeserialized) {
      throw new IllegalThreadStateException(
          "This thread was deserialized from one that had already terminated");
    }
    super.start();
  }

  private void writeObject(final ObjectOutputStream out) throws IOException {
    lock.lock();
    try {
      interrupted = isInterrupted();
      daemon = isDaemon();
      name = getName();
      priority = getPriority();
      state = getState();
      serialGroup = serializableOrNull(getThreadGroup());
      contextClassLoader = serializableOrNull(getContextClassLoader());
      serialUncaughtExceptionHandler = serializableOrNull(getUncaughtExceptionHandler());
      serialTarget = serializableOrNull(target);
      out.defaultWriteObject();
    } finally {
      lock.unlock();
    }
  }

  /** Clones this LooperThread using {@link CloneViaSerialization#clone(Serializable)}. */
  @SuppressWarnings("MethodDoesntCallSuperMethod") @Override public LooperThread clone() {
    return CloneViaSerialization.clone(this);
  }
}
