package com.github.ccbar.settings.ui.components

import com.github.ccbar.settings.QuickParamConfig
import com.github.ccbar.settings.ui.QuickParamEditDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.BoxLayout

/**
 * 快捷参数摘要面板
 * 显示快捷参数摘要和编辑按钮
 */
class QuickParamSummaryPanel(
    private val project: Project?,
    private val labelText: String = "快捷参数:",
    private val hintText: String = "若和公共参数同名，则覆盖公共参数"
) {
    private val summaryField = JBTextField().apply {
        isEditable = false
    }

    private var quickParams: MutableList<QuickParamConfig> = mutableListOf()

    /**
     * 获取面板组件
     */
    fun createPanel(): JPanel {
        val outerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(JLabel(labelText), BorderLayout.WEST)
        mainPanel.add(summaryField, BorderLayout.CENTER)

        val editButton = JButton(AllIcons.Actions.Edit).apply {
            toolTipText = "编辑快捷参数"
            addActionListener { openEditDialog() }
        }
        mainPanel.add(editButton, BorderLayout.EAST)

        outerPanel.add(mainPanel)

        // 添加提示标签
        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(
            Box.createHorizontalStrut(JLabel(labelText).preferredSize.width),
            BorderLayout.WEST
        )
        hintPanel.add(JLabel(hintText).apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)
        outerPanel.add(hintPanel)

        return outerPanel
    }

    /**
     * 设置快捷参数数据并更新摘要
     */
    fun setQuickParams(params: List<QuickParamConfig>) {
        quickParams = params.toMutableList()
        updateSummary()
    }

    /**
     * 获取编辑后的快捷参数
     */
    fun getQuickParams(): List<QuickParamConfig> = quickParams.toList()

    /**
     * 更新摘要显示
     */
    fun updateSummary() {
        val names = quickParams.filter { it.enabled }.map { it.name }
        summaryField.text = if (names.isEmpty()) "" else names.joinToString(" | ")
    }

    /**
     * 清空摘要
     */
    fun clear() {
        quickParams.clear()
        summaryField.text = ""
    }

    private fun openEditDialog() {
        val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        val deepCopy = quickParams.map { it.deepCopy() }
        val dialog = QuickParamEditDialog(currentProject, deepCopy)
        if (dialog.showAndGet()) {
            quickParams.clear()
            quickParams.addAll(dialog.getEditedQuickParams())
            updateSummary()
        }
    }

    companion object {
        /**
         * 创建用于 CommandBar 公共快捷参数的面板
         */
        fun createForCommonQuickParams(project: Project?): QuickParamSummaryPanel {
            return QuickParamSummaryPanel(
                project = project,
                labelText = "快捷参数(公共):",
                hintText = "对命令列表中的所有命令生效，对直接命令无效"
            )
        }

        /**
         * 创建用于 Command 快捷参数的面板
         */
        fun createForCommandQuickParams(project: Project?): QuickParamSummaryPanel {
            return QuickParamSummaryPanel(
                project = project,
                labelText = "快捷参数:",
                hintText = "若和公共参数同名，则覆盖公共参数"
            )
        }
    }
}
