import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class MemoryAllocationLab {

//Data structures 
    static class MemoryBlock {
        int start;           // inclusive start address (KB index)
        int size;            // length in KB
        String processName;  // null => free

        MemoryBlock(int start, int size, String processName) {
            this.start = start;
            this.size = size;
            this.processName = processName;
        }
        boolean isFree() { return processName == null; }
        int endExclusive() { return start + size; }
        int endInclusive() { return start + size - 1; }
    }

    // ----- Simulator state -----
    private static final ArrayList<MemoryBlock> memory = new ArrayList<>();
    private static int totalMemoryKB = 0;
    private static int successfulAllocations = 0;
    private static int failedAllocations = 0;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java MemoryAllocationLab <requests-file>");
            return;
        }
        String path = args[0];

        System.out.println("========================================");
        System.out.println("Memory Allocation Simulator (First-Fit)");
        System.out.println("========================================\n");
        System.out.println("Reading from: " + path);

        processRequests(path);
        printFinalState();
        displayStatistics();
    }

    //Read file and run requests 
    private static void processRequests(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String first = br.readLine();
            if (first == null) throw new IOException("Empty input file.");
            totalMemoryKB = Integer.parseInt(first.trim());
            System.out.println("Total Memory: " + totalMemoryKB + " KB");
            System.out.println("----------------------------------------\n");
            System.out.println("Processing requests...\n");

            memory.clear();
            memory.add(new MemoryBlock(0, totalMemoryKB, null));

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toUpperCase();

                if ("REQUEST".equals(cmd) && parts.length == 3) {
                    String proc = parts[1];
                    int size = Integer.parseInt(parts[2]);
                    allocate(proc, size);
                } else if ("RELEASE".equals(cmd) && parts.length == 2) {
                    String proc = parts[1];
                    deallocate(proc);
                } else {
                    System.out.println("Ignoring malformed line: " + line);
                }
            }
        } catch (IOException ioe) {
            System.out.println("Error reading file: " + ioe.getMessage());
        } catch (NumberFormatException nfe) {
            System.out.println("Invalid number in input file: " + nfe.getMessage());
        }
    }

    // ====== First-Fit allocate ======
    private static void allocate(String processName, int sizeKB) {
        for (int i = 0; i < memory.size(); i++) {
            MemoryBlock block = memory.get(i);
            if (block.isFree() && block.size >= sizeKB) {
                if (block.size > sizeKB) {
                    int remaining = block.size - sizeKB;
                    block.size = sizeKB;
                    block.processName = processName;
                    MemoryBlock leftover = new MemoryBlock(block.endExclusive(), remaining, null);
                    memory.add(i + 1, leftover);
                } else {
                    block.processName = processName;
                }
                successfulAllocations++;
                System.out.println("REQUEST " + processName + " " + sizeKB + " KB -> SUCCESS");
                return;
            }
        }
        failedAllocations++;
        System.out.println("REQUEST " + processName + " " + sizeKB + " KB -> FAILED (insufficient memory)");
    }

    //Deallocate and merge neighbors
    private static void deallocate(String processName) {
        for (int i = 0; i < memory.size(); i++) {
            MemoryBlock block = memory.get(i);
            if (!block.isFree() && block.processName.equals(processName)) {
                block.processName = null;
                System.out.println("RELEASE " + processName + " -> SUCCESS");
                mergeAdjacentBlocks();
                return;
            }
        }
        System.out.println("RELEASE " + processName + " -> FAILED (process not found)");
    }

    private static void mergeAdjacentBlocks() {
        for (int i = 0; i < memory.size() - 1; i++) {
            MemoryBlock cur = memory.get(i);
            MemoryBlock nxt = memory.get(i + 1);
            if (cur.isFree() && nxt.isFree() && cur.endExclusive() == nxt.start) {
                cur.size += nxt.size;
                memory.remove(i + 1);
                i--;
            }
        }
    }

    private static void printFinalState() {
        System.out.println("\n========================================");
        System.out.println("Final Memory State");
        System.out.println("========================================");
        for (int i = 0; i < memory.size(); i++) {
            MemoryBlock b = memory.get(i);
            String idx = String.format("Block %d:", i + 1);
            String range = String.format("[%d-%d]", b.start, b.endInclusive());
            if (b.isFree()) {
                System.out.printf("%-8s %-12s  FREE (%d KB)%n", idx, range, b.size);
            } else {
                System.out.printf("%-8s %-12s  %s (%d KB) - ALLOCATED%n",
                        idx, range, b.processName, b.size);
            }
        }
    }

    private static void displayStatistics() {
        int allocated = 0;
        int free = 0;
        int procCount = 0;
        int freeBlocks = 0;
        int largestFree = 0;

        for (MemoryBlock b : memory) {
            if (b.isFree()) {
                free += b.size;
                freeBlocks++;
                if (b.size > largestFree) largestFree = b.size;
            } else {
                allocated += b.size;
                procCount++;
            }
        }

        double allocPct = totalMemoryKB == 0 ? 0.0 : (allocated * 100.0) / totalMemoryKB;
        double freePct  = totalMemoryKB == 0 ? 0.0 : (free * 100.0) / totalMemoryKB;
        double extFragPct = 0.0;
        if (free > 0) extFragPct = (1.0 - (largestFree * 1.0 / free)) * 100.0;

        System.out.println("\n========================================");
        System.out.println("Memory Statistics");
        System.out.println("========================================");
        System.out.printf("Total Memory:           %d KB%n", totalMemoryKB);
        System.out.printf("Allocated Memory:       %d KB (%.2f%%)%n", allocated, allocPct);
        System.out.printf("Free Memory:            %d KB (%.2f%%)%n", free, freePct);
        System.out.printf("Number of Processes:    %d%n", procCount);
        System.out.printf("Number of Free Blocks:  %d%n", freeBlocks);
        System.out.printf("Largest Free Block:     %d KB%n", largestFree);
        System.out.printf("External Fragmentation: %.2f%%%n%n", extFragPct);
        System.out.printf("Successful Allocations: %d%n", successfulAllocations);
        System.out.printf("Failed Allocations:     %d%n", failedAllocations);
        System.out.println("========================================");
    }
}
