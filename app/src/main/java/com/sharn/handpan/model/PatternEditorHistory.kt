package com.sharn.handpan.model

/** Snapshot history for editor mutations. Each applied snapshot is one undo step. */
class PatternEditorHistory<T>(
    initial: List<T>,
    private val limit: Int = 50
) {
    private val undoStack = ArrayDeque<List<T>>()
    private val redoStack = ArrayDeque<List<T>>()
    var current: List<T> = initial.toList()
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun apply(next: List<T>) {
        if (next == current) return
        undoStack.addLast(current)
        if (undoStack.size > limit) undoStack.removeFirst()
        current = next.toList()
        redoStack.clear()
    }

    fun undo(): List<T>? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): List<T>? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        return current
    }

    fun clearRedoForTestOnly() {
        redoStack.clear()
    }
}
