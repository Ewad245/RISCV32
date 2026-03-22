//
// Created by Kuno on 3/22/2026.
//
#include "syscall.h"
#include "ulib.h"

// Define the Linked List Node
typedef struct Node {
    int data;
    struct Node* next;
} Node;

// Function to insert a new node in ascending sorted order
Node* insert_sorted(Node* head, int val) {
    // 1. Test malloc!
    Node* new_node = (Node*)malloc(sizeof(Node));

    if (new_node == NULL) {
        print("Error: Memory allocation failed!\n");
        exit(1);
    }

    new_node->data = val;
    new_node->next = NULL;

    // Case A: Empty list or new node should be the new head
    if (head == NULL || head->data >= val) {
        new_node->next = head;
        return new_node;
    }

    // Case B: Traverse to find the correct insertion point
    Node* current = head;
    while (current->next != NULL && current->next->data < val) {
        current = current->next;
    }

    // Insert the node
    new_node->next = current->next;
    current->next = new_node;

    return head;
}

// Function to print the entire list
void print_list(Node* head) {
    Node* current = head;
    print("List: ");
    while (current != NULL) {
        printf("%d -> ", current->data);
        current = current->next;
    }
    print("NULL\n");
}

// Function to free all nodes in the list
void free_list(Node* head) {
    Node* current = head;
    Node* next_node;

    int count = 0;
    while (current != NULL) {
        next_node = current->next; // Save the next pointer
        free(current);             // 2. Test free!
        current = next_node;       // Move to the next node
        count++;
    }

    printf("Successfully freed %d nodes.\n", count);
}

int main() {
    print("\n--- Dynamic Memory (Linked List) Test ---\n");
    Node* head = NULL;

    print("1. Inserting random elements...\n");
    head = insert_sorted(head, 42);
    head = insert_sorted(head, 10);
    head = insert_sorted(head, 99);
    head = insert_sorted(head, 23);
    head = insert_sorted(head, 7);
    head = insert_sorted(head, 50);

    print("2. Displaying the sorted list:\n");
    print_list(head);

    print("3. Cleaning up memory...\n");
    free_list(head);

    // Optional: Test a massive allocation to trigger morecore()/sbrk() heavily
    print("4. Testing bulk allocation...\n");
    int* big_array = (int*)malloc(1000 * sizeof(int));
    if (big_array != NULL) {
        big_array[0] = 1234;
        big_array[999] = 5678;
        print("Bulk allocation successful and writable.\n");
        free(big_array);
        print("Bulk allocation freed.\n");
    }

    print("--- Test Complete ---\n");
    exit(0);
    return 0;
}