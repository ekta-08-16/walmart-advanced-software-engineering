import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * A "power of two max heap": a d-ary max-heap where the number of children
 * per parent, d, is constrained to be a power of two (d = 2^childExponent).
 *
 * The heap is backed by a single growable array, exactly like a classic
 * binary heap, but the parent/child index arithmetic is generalized to an
 * arbitrary branching factor. Restricting that branching factor to a power
 * of two means it could be implemented via a bit-shift instead of a general
 * division/multiplication, which is the performance hook the branching
 * factor is named for (branchingFactorShiftAmount below).
 *
 * Supported operations (as required by the benchmark):
 *   - insert(value): O(log_d n) amortized
 *   - popMax():      O(d * log_d n) worst case
 *
 * Both operations only ever touch array slots that are in use, so the cost
 * of a very large branching factor is bounded by the current heap size, not
 * by the branching factor itself. This matters a lot here: with
 * childExponent = 30 a naive implementation might try to scan ~10^9
 * "children" per node, but this implementation clamps every scan to the
 * number of elements actually present.
 *
 * Not thread-safe.
 */
public class PowerOfTwoMaxHeap<T extends Comparable<T>> {

    // Largest exponent we allow. 1L << 62 is still a positive, well-defined
    // long, and no realistic heap will ever hold anywhere near that many
    // elements, so this is effectively "unbounded" while staying safe from
    // shift-overflow/undefined-shift-amount bugs at the boundary (a shift of
    // 63 or 64 on a long is either negative or a no-op in Java, neither of
    // which is a sane branching factor).
    private static final int MAX_CHILD_EXPONENT = 62;

    private static final int DEFAULT_CAPACITY = 16;

    // How many children each parent node has, i.e. 2^childrenExponent.
    // Stored as the exponent (the "shift amount") rather than the raw
    // branching factor so that child/parent index math can use a fast
    // bit-shift instead of a multiply/divide, and so the value can never
    // silently overflow regardless of how large it is.
    private final int branchingFactorShiftAmount;

    // 1L << branchingFactorShiftAmount, cached so we don't recompute it on
    // every insert/pop. Kept as a long because for large shift amounts this
    // number vastly exceeds any array length we could ever allocate.
    private final long branchingFactor;

    private Object[] elements;
    private int size;

    /**
     * @param childrenExponent the exponent x such that every parent node has
     *                          2^x children. Must be >= 0 and <= 62.
     */
    public PowerOfTwoMaxHeap(int childrenExponent) {
        if (childrenExponent < 0) {
            throw new IllegalArgumentException(
                    "childrenExponent must be non-negative, got " + childrenExponent);
        }
        if (childrenExponent > MAX_CHILD_EXPONENT) {
            throw new IllegalArgumentException(
                    "childrenExponent must be <= " + MAX_CHILD_EXPONENT + ", got " + childrenExponent);
        }
        this.branchingFactorShiftAmount = childrenExponent;
        this.branchingFactor = 1L << childrenExponent;
        this.elements = new Object[DEFAULT_CAPACITY];
        this.size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Returns the maximum element without removing it. */
    @SuppressWarnings("unchecked")
    public T peekMax() {
        if (size == 0) {
            throw new NoSuchElementException("Heap is empty");
        }
        return (T) elements[0];
    }

    /**
     * Inserts a value into the heap, maintaining the heap property.
     */
    public void insert(T value) {
        if (value == null) {
            throw new NullPointerException("Cannot insert null into the heap");
        }
        ensureCapacity(size + 1);
        elements[size] = value;
        siftUp(size);
        size++;
    }

    /**
     * Removes and returns the maximum element in the heap.
     *
     * @throws NoSuchElementException if the heap is empty
     */
    @SuppressWarnings("unchecked")
    public T popMax() {
        if (size == 0) {
            throw new NoSuchElementException("Heap is empty");
        }
        T max = (T) elements[0];
        int lastIndex = size - 1;
        elements[0] = elements[lastIndex];
        elements[lastIndex] = null; // avoid memory leak / stray reference
        size--;
        if (size > 0) {
            siftDown(0);
        }

        return max;
    }

    /**
     * Moves the element at {@code index} up toward the root until the heap
     * property is restored.
     */
    private void siftUp(int index) {
        Object value = elements[index];
        while (index > 0) {
            // Using long arithmetic here (branchingFactor is a long) keeps
            // this correct even for huge branching factors; the result is
            // always a valid array index (< index), so the cast back to int
            // is safe.
            int parentIndex = (int) ((index - 1) / branchingFactor);
            Object parentValue = elements[parentIndex];
            if (compare(value, parentValue) <= 0) {
                break;
            }
            elements[index] = parentValue;
            index = parentIndex;
        }
        elements[index] = value;
    }

    /**
     * Moves the element at {@code index} down toward the leaves until the
     * heap property is restored. Only ever examines children that actually
     * exist in the array, so this stays cheap even when the configured
     * branching factor is astronomically larger than the heap itself.
     */
    private void siftDown(int index) {
        Object value = elements[index];
        while (true) {
            // Guard against overflow of (index * branchingFactor): when the
            // branching factor already exceeds the heap size, any non-root
            // node (index != 0) is guaranteed to have zero children in
            // range, since index * branchingFactor >= branchingFactor > size
            // for index >= 1. Short-circuiting here means the multiplication
            // below is only ever performed when branchingFactor <= size, in
            // which case index * branchingFactor <= size * size, which is
            // always safely representable as a long.
            if (index != 0 && branchingFactor > size) {
                break;
            }
            long firstChildIndexLong = (long) index * branchingFactor + 1;
            if (firstChildIndexLong >= size) {
                break; // no children in range
            }
            int firstChildIndex = (int) firstChildIndexLong;

            // Number of real children this node has, clamped to both the
            // configured branching factor and how many elements remain.
            long childrenRemaining = (long) size - firstChildIndex;
            int childCount = (int) Math.min(branchingFactor, childrenRemaining);

            int largestChildIndex = firstChildIndex;
            Object largestChildValue = elements[firstChildIndex];
            for (int offset = 1; offset < childCount; offset++) {
                int candidateIndex = firstChildIndex + offset;
                Object candidateValue = elements[candidateIndex];
                if (compare(candidateValue, largestChildValue) > 0) {
                    largestChildIndex = candidateIndex;
                    largestChildValue = candidateValue;
                }
            }

            if (compare(largestChildValue, value) <= 0) {
                break;
            }
            elements[index] = largestChildValue;
            index = largestChildIndex;
        }
        elements[index] = value;
    }

    @SuppressWarnings("unchecked")
    private int compare(Object a, Object b) {
        return ((T) a).compareTo((T) b);
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= elements.length) {
            return;
        }
        int newCapacity = elements.length + (elements.length >> 1) + 1; // grow by 1.5x
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }
        elements = Arrays.copyOf(elements, newCapacity);
    }

    // ------------------------------------------------------------------
    // Self-tests / benchmarking harness
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        testBasicOrdering(0);
        testBasicOrdering(1);
        testBasicOrdering(2);
        testBasicOrdering(4);
        testBasicOrdering(30); // very large branching factor, small heap
        testRandomAgainstReference(0, 2000);
        testRandomAgainstReference(1, 5000);
        testRandomAgainstReference(2, 5000);
        testRandomAgainstReference(3, 5000);
        testRandomAgainstReference(10, 5000);
        testRandomAgainstReference(31, 5000);
        testRandomAgainstReference(62, 5000); // max allowed exponent
        testDuplicatesAndSingleElement();
        testEmptyHeapThrows();
        testInvalidConstructorArgsThrow();
        testInterleavedInsertPop();
        benchmark(3, 1_000_000);
        benchmark(10, 1_000_000);
        System.out.println("All tests passed.");
    }

    private static void testBasicOrdering(int exponent) {
        PowerOfTwoMaxHeap<Integer> heap = new PowerOfTwoMaxHeap<>(exponent);
        int[] values = {5, 3, 8, 1, 9, 2, 7, 4, 6, 0};
        for (int v : values) {
            heap.insert(v);
        }
        Integer previous = null;
        for (int i = 0; i < values.length; i++) {
            int max = heap.popMax();
            if (previous != null && max > previous) {
                throw new AssertionError("Heap property violated for exponent " + exponent);
            }
            previous = max;
        }
        if (!heap.isEmpty()) {
            throw new AssertionError("Heap should be empty for exponent " + exponent);
        }
    }

    private static void testRandomAgainstReference(int exponent, int count) {
        java.util.Random random = new java.util.Random(42 + exponent);
        PowerOfTwoMaxHeap<Integer> heap = new PowerOfTwoMaxHeap<>(exponent);
        Integer[] reference = new Integer[count];
        for (int i = 0; i < count; i++) {
            int value = random.nextInt();
            reference[i] = value;
            heap.insert(value);
        }
        Arrays.sort(reference, java.util.Collections.reverseOrder());
        for (int i = 0; i < count; i++) {
            int expected = reference[i];
            int actual = heap.popMax();
            if (expected != actual) {
                throw new AssertionError("Mismatch at index " + i + " for exponent " + exponent
                        + ": expected " + expected + " got " + actual);
            }
        }
        if (!heap.isEmpty()) {
            throw new AssertionError("Heap not empty after draining, exponent " + exponent);
        }
    }

    private static void testDuplicatesAndSingleElement() {
        PowerOfTwoMaxHeap<Integer> heap = new PowerOfTwoMaxHeap<>(2);
        heap.insert(5);
        if (heap.popMax() != 5 || !heap.isEmpty()) {
            throw new AssertionError("Single-element case failed");
        }
        for (int i = 0; i < 100; i++) {
            heap.insert(7);
        }
        for (int i = 0; i < 100; i++) {
            if (heap.popMax() != 7) {
                throw new AssertionError("Duplicate handling failed");
            }
        }
    }

    private static void testEmptyHeapThrows() {
        PowerOfTwoMaxHeap<Integer> heap = new PowerOfTwoMaxHeap<>(1);
        boolean threw = false;
        try {
            heap.popMax();
        } catch (NoSuchElementException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("popMax on empty heap should throw");
        }
    }

    private static void testInvalidConstructorArgsThrow() {
        boolean threw = false;
        try {
            new PowerOfTwoMaxHeap<Integer>(-1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Negative childrenExponent should throw");
        }

        threw = false;
        try {
            new PowerOfTwoMaxHeap<Integer>(63);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Excessively large childrenExponent should throw");
        }
    }

    private static void testInterleavedInsertPop() {
        PowerOfTwoMaxHeap<Integer> heap = new PowerOfTwoMaxHeap<>(3);
        java.util.PriorityQueue<Integer> reference =
                new java.util.PriorityQueue<>(java.util.Collections.reverseOrder());
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 20000; i++) {
            if (reference.isEmpty() || random.nextBoolean()) {
                int value = random.nextInt(1000);
                heap.insert(value);
                reference.add(value);
            } else {
                int expected = reference.poll();
                int actual = heap.popMax();
                if (expected != actual) {
                    throw new AssertionError("Interleaved mismatch: expected " + expected + " got " + actual);
                }
            }
        }
        while (!reference.isEmpty()) {
            int expected = reference.poll();
            int actual = heap.popMax();
            if (expected != actual) {
                throw new AssertionError("Interleaved drain mismatch: expected " + expected + " got " + actual);
            }
        }
    }

    private static void benchmark(int exponent, int count) {
        PowerOfTwoMaxHeap<Integer> heap = new PowerOfTwoMaxHeap<>(exponent);
        java.util.Random random = new java.util.Random(1);
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            heap.insert(random.nextInt());
        }
        long afterInsert = System.nanoTime();
        for (int i = 0; i < count; i++) {
            heap.popMax();
        }
        long afterPop = System.nanoTime();
        System.out.printf(
                "exponent=%d, n=%d, insert=%.2fms, popMax=%.2fms%n",
                exponent, count,
                (afterInsert - start) / 1_000_000.0,
                (afterPop - afterInsert) / 1_000_000.0);
    }
}
