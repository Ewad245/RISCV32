# RISC-V Datapath Visualization Guide

A guide to understanding the datapath diagram in the CSE311 CPU simulation.

---

## Wire Colors

### 🔴 Red Lines - Active Data Flow
**Meaning:** Data is actively flowing through this wire during the current instruction.

When a wire turns red (with a thicker stroke and glow), it indicates:
- This path is **being used** by the current instruction
- Data is traveling from one component to another along this wire

### 🟡 Yellow Dots - Animated Data Packets  
**Meaning:** Visual animation showing the **direction** of data movement.

The small golden circles with orange borders that move along wires:
- Show which **direction** data is flowing
- Help visualize the **sequence** of operations
- Appear on active (red) wires to illustrate data transfer
- Animate once per instruction cycle, then disappear

### ⚪ Gray Lines - Inactive Wires
**Meaning:** This wire is **not used** by the current instruction.

---

## Component Colors

| Component | Color | Purpose |
|-----------|-------|---------|
| **PC** | Purple stroke, white gradient fill | Holds current instruction address |
| **Control Unit** | Red stroke, white gradient fill | Decodes instruction, generates control signals |
| **Memory** | Green stroke, white gradient fill | Stores instructions (I-Mem) and data (D-Mem) |
| **Registers** | Teal stroke, white gradient fill | 32 general-purpose registers (x0-x31) |
| **ALU** | Orange stroke, white gradient fill | Performs arithmetic/logic operations |
| **Imm Gen** | Cyan stroke, white gradient fill | Extracts immediate values from instructions |
| **MUX** | Indigo stroke, white gradient fill | Selects between multiple inputs |

> **Note:** All components have:
> - White to light-tint gradient fills
> - Drop shadow effects for depth
> - Hover effects (stroke darkens, shadow intensifies when you mouse over)

---

## Instruction Type → Active Paths

### R-Type (ADD, SUB, AND, OR, XOR, etc.)
```
PC → I-Mem → Registers → ALU → MUX → Registers (writeback)
                ↓         ↑
              rs1,rs2   result
```
Both register operands flow to ALU, result writes back.

### I-Type (ADDI, ANDI, SLTI, etc.)
```
PC → I-Mem → Registers → ALU → MUX → Registers
        ↓        ↓        ↑
      Imm Gen → immediate─┘
```
One register + immediate value to ALU.

### Load (LB, LH, LW, LBU, LHU)
```
PC → I-Mem → Registers → ALU → D-Mem → MUX → Registers
        ↓        ↓        ↑      ↓
      Imm Gen → offset───┘    read data
```
Address calculated by ALU, data read from memory.

### Store (SB, SH, SW)
```
PC → I-Mem → Registers → ALU → D-Mem
        ↓        ↓   ↓     ↑
      Imm Gen → offset─────┘
                 └──────→ write data
```
Address to D-Mem, register data written to memory.

### Branch (BEQ, BNE, BLT, BGE, etc.)
```
PC → I-Mem → Registers → ALU (compare)
        ↓         ↓ ↓
      Imm Gen   rs1 rs2
```
Two registers compared in ALU.

### JAL / JALR
```
PC → I-Mem → Imm Gen → (calculate target)
                    ↓
              MUX → Registers (save PC+4)
```
PC+4 saved to rd, jump to target address.

---

## Control Signals (Top Right of Control Unit)

| Signal | Green = Active | Gray = Inactive |
|--------|----------------|-----------------|
| **RegWrite** | Writing to register file | No register write |
| **MemRead** | Reading from D-Mem | No memory read |
| **MemWrite** | Writing to D-Mem | No memory write |
| **ALUSrc** | Using immediate value | Using register value |

---

## Tips for Reading the Datapath

1. **Start at PC** - Every instruction begins here
2. **Follow the red wires** - They show the active path
3. **Watch the yellow dots** - They show data direction
4. **Check Control signals** - They explain what operations are happening
5. **Hover for tooltips** - Each component has a description

---

# Paging Visualization Guide

This document describes how the **Paging Memory View** is rendered in the Dashboard, explaining the color coding scheme and the connection between the GUI (`MemoryView.java`) and the Kernel backend (`PagedMemoryManager.java`).

## 1. Visual Overview

The Paging View represents the **Physical Memory (RAM)** of the simulated computer.
- The entire memory is divided into **4KB Frames**.
- Each square in the grid represents **one physical frame**.
- The position of the square corresponds to the **Physical Frame Number (PPN)**.

### Color Legend

The color of each frame indicates its current allocation status and ownership:

| Color | Meaning | Condition (Backend Logic) |
| :--- | :--- | :--- |
| <span style="color:silver">**LIGHT GRAY**</span> | **Free / Unused** | `FrameOwner == null` |
| <span style="color:gray">**DARK GRAY**</span> | **Page Table** | `FrameOwner.pid == -1` (Reserved by OS for paging structures) |
| <span style="color:salmon">**SALMON**</span> | **Process A** | `pid % 7 == 0` |
| <span style="color:lightblue">**LIGHT BLUE**</span> | **Process B** | `pid % 7 == 1` |
| <span style="color:orange">**ORANGE**</span> | **Process C** | `pid % 7 == 2` |
| <span style="color:violet">**VIOLET**</span> | **Process D** | `pid % 7 == 3` |
| <span style="color:cyan">**CYAN**</span> | **Process E** | `pid % 7 == 4` |
| <span style="color:gold">**GOLD**</span> | **Process F** | `pid % 7 == 5` |
| <span style="color:pink">**PINK**</span> | **Process G** | `pid % 7 == 6` |

> **Note:** Process colors cycle through the 7 distinct colors defined above based on the formula: `colors[pid % 7]`.

### Fill Style Legend (Allocated Frames)

For frames allocated to user processes, there are two distinct visual styles:

| Style | Visual | Meaning |
| :--- | :--- | :--- |
| **Solid Fill** | Colored box with black border | Frame contains **actual data** (non-zero bytes) |
| **Hollow Outline** | White box with colored border + diagonal line | Frame is **allocated but empty** (all zeros) |

This distinction helps you understand:
- **Solid frames** = Memory actively used by the program (code, data, stack with values)
- **Hollow frames** = Memory reserved but not yet written to (e.g., pre-allocated stack space)

> **Technical Detail:** The `isFrameEmpty()` method samples 64 words across each 4KB frame. If all sampled words are zero, the frame is considered "empty."

### Brightness = Access Heat

Solid-filled frames change **brightness** based on recent access frequency:

| Brightness | Meaning |
| :--- | :--- |
| **Dimmer (55% brightness)** | Frame hasn't been accessed recently |
| **Brighter (up to 110% brightness)** | Frame is being frequently accessed |

The heat value ranges from 0-255 internally and modulates the frame's brightness in real-time. This helps you identify:
- **Hot spots** in memory (frequently accessed data/code)
- **Cold regions** (rarely accessed memory)

> **Technical Detail:** Brightness = 0.55 + (heat / 255) × 0.55, so cold frames are at 55% brightness and hot frames reach up to 110% brightness.

---

## 2. GUI Logic (`MemoryView.java`)

The rendering logic is located in the `drawPaging` method.

### Grid Calculation
The view dynamically calculates how many frames fit in a row based on the window width:
```java
double boxSize = 15; // Size of one frame square
double gap = 1;      // Spacing between squares
int cols = (int) (w / (boxSize + gap)); // Columns per row
```

### Frame Rendering Logic
```java
FrameOwner owner = frames[i];
if (owner == null) {
    // Free frame - light gray solid
} else if (owner.pid == -1) {
    // Page Table Frame - dark gray solid
} else {
    // User frame - check if it has data
    boolean hasData = !pmm.isFrameEmpty(i);
    if (hasData) {
        // Apply heat-based brightness modulation
        double heatVal = heat[i] / 255.0;
        Color displayColor = frameColor.deriveColor(0, 1.0, 0.55 + heatVal * 0.55, 1.0);
        // Solid fill with heat-adjusted color
    } else {
        // Hollow outline with diagonal line
    }
}
```

---

## 3. Tooltip Information

Hover over any frame to see detailed information:

### Free Frame
```
Frame: 0
Status: Free
Real Address: 0x00000000
```

### Page Table Frame
```
Frame: 1
Status: Page Table
Real Address: 0x00001000
```

### User Data Frame
```
Frame: 6
Real Address: 0x00006000
-----------------
Mapped to PID: 2
Virtual Page: 0x7FFFD
-----------------
Has Data: NO (all zeros)
Heat: 45/255
```

---

## 4. Understanding "Allocated but Empty"

A common scenario is seeing **hollow frames in the stack region**:

1. When a process starts, the OS allocates stack pages (e.g., at virtual address `0x7FFFD000`)
2. Physical frames are reserved and zero-filled
3. Until the program pushes data to the stack, the frames remain empty
4. As the program executes (function calls, local variables), the frames fill with data and become solid

This is **normal behavior**, not a bug!

---

## 5. Locality Strips (Bottom Section)

Below the frame grid, you'll see **locality strips** for each process:

### What They Show
Each strip visualizes how **contiguously** a process's frames are allocated in physical memory:

| Visual Element | Meaning |
| :--- | :--- |
| **Gray background bar** | Full span from lowest to highest frame owned by the process |
| **Colored ticks** | Individual frames actually owned by the process |
| **Badge (Good/Moderate/Poor)** | Locality score as a percentage |

### Locality Score Calculation
```
Locality % = (Number of frames owned) / (Span of frames) × 100
```

| Score | Badge | Interpretation |
| :--- | :--- | :--- |
| **≥80%** | Good (Green) | Frames are tightly packed, efficient memory use |
| **50-79%** | Moderate (Amber) | Some fragmentation |
| **<50%** | Poor (Red) | Highly scattered frames, potential performance issue |

### Example
If Process 2 owns frames 10, 11, 12, 15, 18:
- Span = 18 - 10 + 1 = 9 frames
- Owned = 5 frames
- Locality = 5/9 × 100 = **56% (Moderate)**

> **Why it matters:** Good locality means the process's memory is concentrated, which can improve cache performance and reduce page table walk overhead. Poor locality indicates fragmentation.

### Display Limitations
- Only the **top processes by frame count** are shown (as many as fit in the view)
- Processes are sorted by frame count (most significant first)
- If there are more processes than space, a truncation indicator shows how many are hidden
