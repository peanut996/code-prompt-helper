package com.github.peanut996.codeprompthelper.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import com.intellij.openapi.util.Iconable

class PromptHelperToolWindow(private val project: Project) {

    private val LOG = logger<PromptHelperToolWindow>()

    val contentPanel: JPanel
    private val treeModel = DefaultTreeModel(CheckedTreeNode(null))
    private val tree: CheckboxTree
    private val tokenCountCache = ConcurrentHashMap<VirtualFile, Int>()
    private val loadingFiles = ConcurrentHashMap.newKeySet<VirtualFile>()

    private fun estimateTokenCount(text: String): Int {
        return text.split(Regex("\\s+|[.,!?;:()\\[\\]{}<>]")).count { it.isNotEmpty() }
    }

    init {
        val panel = SimpleToolWindowPanel(true, true)

        val rootNode = CheckedTreeNode(project)
        rootNode.isChecked = false
        treeModel.setRoot(rootNode)
        tree = CheckboxTree(FileCheckboxTreeCellRenderer(), rootNode)
        tree.selectionModel.selectionMode = TreeSelectionModel.CONTIGUOUS_TREE_SELECTION
        tree.isRootVisible = false
        tree.emptyText.text = "Click 'Refresh' to load project files"

        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("CodePromptHelper.ToolWindowToolbarActions") as DefaultActionGroup
        val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, actionGroup, true)
        toolbar.targetComponent = tree
        panel.toolbar = toolbar.component

        val scrollPane = ScrollPaneFactory.createScrollPane(tree)
        panel.setContent(scrollPane)

        contentPanel = panel

        refreshTree()
    }

    fun refreshTree() {
        val projectDir = project.baseDir ?: run {
            tree.emptyText.text = "Cannot determine project directory."
            LOG.warn("Project baseDir is null: ${project.name}")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning Project Files", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Scanning project structure..."

                val newRoot = CheckedTreeNode(project)
                newRoot.isChecked = false
                buildTree(projectDir, newRoot, indicator)

                ApplicationManager.getApplication().invokeLater {
                    val currentRoot = treeModel.root as CheckedTreeNode
                    currentRoot.removeAllChildren()
                    for (i in 0 until newRoot.childCount) {
                        treeModel.insertNodeInto(newRoot.getChildAt(i) as CheckedTreeNode, currentRoot, i)
                    }
                    treeModel.nodeStructureChanged(currentRoot)
                    tree.emptyText.text = if (currentRoot.childCount == 0) "No relevant files found." else StatusText.getDefaultEmptyText()
                    LOG.info("Project tree refreshed.")
                }
            }
        })
    }

    private fun buildTree(dir: VirtualFile, parentNode: CheckedTreeNode, indicator: ProgressIndicator) {
        indicator.checkCanceled()
        indicator.text2 = dir.path

        val children = ReadAction.compute<Array<VirtualFile>?, RuntimeException> {
            try { dir.children } catch (e: Exception) { null }
        } ?: return

        children.sortWith(compareBy<VirtualFile> { !it.isDirectory }.thenBy { it.name.lowercase() })

        for (child in children) {
            indicator.checkCanceled()
            if (shouldIncludeFile(child)) {
                val childNode = CheckedTreeNode(child)
                childNode.isChecked = false
                parentNode.add(childNode)
                if (child.isDirectory) {
                    buildTree(child, childNode, indicator)
                }
            }
        }
    }

    private fun shouldIncludeFile(file: VirtualFile): Boolean {
        return try {
            ReadAction.compute<Boolean, RuntimeException> {
                val name = file.name
                !name.startsWith(".") &&
                        name !in listOf("build", "dist", "target", "out", ".gradle", ".idea", "node_modules") &&
                        project.isOpen &&
                        !FileTypeRegistry.getInstance().isFileIgnored(file) &&
                        !ProjectRootManager.getInstance(project).fileIndex.isExcluded(file)
            }
        } catch (e: Exception) {
            LOG.warn("Error checking if file should be included: ${file.path}", e)
            false
        }
    }

    fun getSelectedFilesContext(addFileInfo: Boolean = true): Pair<String, Int> {
        val selectedFiles = tree.getCheckedNodes(VirtualFile::class.java) { true }
        if (selectedFiles.isEmpty()) return Pair("", 0)

        val contentBuilder = StringBuilder()
        val totalTokens = AtomicInteger(0)
        val filesToProcess = mutableListOf<VirtualFile>()

        selectedFiles.forEach { file -> collectFilesRecursively(file, filesToProcess) }

        val uniqueFiles = filesToProcess.distinct()

        ProgressManager.getInstance().run(object : Task.Modal(project, "Gathering Selected Context", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Reading files and estimating tokens..."
                val totalFiles = uniqueFiles.size.toDouble()

                uniqueFiles.forEachIndexed { index, file ->
                    indicator.checkCanceled()
                    indicator.fraction = (index + 1) / totalFiles
                    indicator.text2 = file.name

                    try {
                        val text = ReadAction.compute<String, IOException> {
                            VfsUtilCore.loadText(file, 10 * 1024 * 1024)
                        }
                        if (addFileInfo) contentBuilder.append("\n\n--- File: ${file.path} ---\n\n")
                        contentBuilder.append(text)
                        totalTokens.addAndGet(estimateTokenCount(text))
                    } catch (e: IOException) {
                        LOG.warn("Failed to read file: ${file.path}", e)
                        contentBuilder.append("\n\n--- Failed to read file: ${file.path} ---\n\n")
                    } catch (e: Exception) {
                        LOG.error("Error processing file: ${file.path}", e)
                        contentBuilder.append("\n\n--- Error processing file: ${file.path} ---\n\n")
                    }
                }
            }
        })

        val header = "--- Combined Context (${uniqueFiles.size} files, estimated ~${totalTokens.get()} tokens) ---\n"
        return Pair(header + contentBuilder.toString(), totalTokens.get())
    }

    private fun collectFilesRecursively(fileOrDir: VirtualFile, collectedFiles: MutableList<VirtualFile>) {
        if (!shouldIncludeFile(fileOrDir)) return

        if (fileOrDir.isDirectory) {
            VfsUtilCore.iterateChildrenRecursively(fileOrDir, ::shouldIncludeFile) { file ->
                if (!file.isDirectory) collectedFiles.add(file)
                true
            }
        } else {
            collectedFiles.add(fileOrDir)
        }
    }

    fun copySelectedContextToClipboard() {
        val (context, _) = getSelectedFilesContext()
        if (context.isNotBlank()) {
            ApplicationManager.getApplication().invokeLater {
                try {
                    CopyPasteManager.getInstance().setContents(StringSelection(context))
                } catch (ex: Exception) {
                    LOG.error("Failed to copy context to clipboard", ex)
                    Messages.showErrorDialog(project, "Failed to copy text to clipboard: ${ex.message}", "Clipboard Error")
                }
            }
        }
    }

    private fun startTokenCalculation(file: VirtualFile, node: CheckedTreeNode) {
        ReadAction.nonBlocking<String?> {
            try {
                VfsUtilCore.loadText(file, 1 * 1024 * 1024)
            } catch (e: IOException) {
                LOG.warn("Could not read file for token count: ${file.path}", e)
                null
            } catch (e: Exception) {
                LOG.error("Error reading file for token count: ${file.path}", e)
                null
            }
        }
            .coalesceBy(this, file)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { text ->
                val count = text?.let { estimateTokenCount(it) } ?: -1
                tokenCountCache[file] = count
                loadingFiles.remove(file)

                val path = TreePath(node.path)
                if (tree.getRowForPath(path) != -1) {
                    treeModel.nodeChanged(node)
                    tree.repaint(tree.getPathBounds(path))
                }
                LOG.debug("Token count for ${file.name}: $count")
            }
            .expireWhen { !project.isOpen || project.isDisposed }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private inner class FileCheckboxTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? CheckedTreeNode ?: return
            val userObject = node.userObject

            var icon: Icon? = null
            var text: String? = null
            var tokenText: String? = null
            var tokenTextColor = SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES

            when (userObject) {
                is Project -> {
                    icon = AllIcons.Nodes.Project
                    text = userObject.name
                }
                is VirtualFile -> {
                    icon = FileIconProvider.EP_NAME.extensionList.firstNotNullOfOrNull {
                        it.getIcon(userObject, Iconable.ICON_FLAG_READ_STATUS, project)
                    } ?: (if (userObject.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Unknown)
                    text = userObject.name

                    if (!userObject.isDirectory) {
                        tokenText = when {
                            loadingFiles.contains(userObject) -> "Calculating..."
                            tokenCountCache.containsKey(userObject) -> {
                                val count = tokenCountCache[userObject]
                                if (count != null && count >= 0) "~${count} tk" else "(Error)".also {
                                    tokenTextColor = SimpleTextAttributes.ERROR_ATTRIBUTES
                                }
                            }
                            else -> {
                                if (loadingFiles.add(userObject)) startTokenCalculation(userObject, node)
                                "(? tk)"
                            }
                        }
                    }
                }
                else -> {
                    text = "Project Content"
                    icon = AllIcons.Nodes.Folder
                }
            }

            textRenderer.icon = icon
            textRenderer.append(text ?: "Unknown", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (tokenText != null) textRenderer.append("  $tokenText", tokenTextColor)

            textRenderer.background = if (selected) UIUtil.getTreeSelectionBackground(hasFocus) else UIUtil.getTreeBackground()
            textRenderer.foreground = if (selected) UIUtil.getTreeSelectionForeground(hasFocus) else UIUtil.getTreeForeground()
        }
    }
} 
