package site.laomst.ccbar.settings.ui.components

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.JComponent

/**
 * 添加 Command 的气泡弹窗
 * 提供添加 Command 和添加分割线两个选项
 */
class AddCommandPopup(
    private val onAddCommand: () -> Unit,
    private val onAddSeparator: () -> Unit
) {
    private var popup: JBPopup? = null
    private var closeTimer: javax.swing.Timer? = null
    private var boundButton: JComponent? = null

    /**
     * 绑定到按钮，设置鼠标悬浮监听
     */
    fun bindToButton(button: JComponent) {
        boundButton = button
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                cancelClose()
                val pt = RelativePoint(button.parent, Point(button.x, button.y + button.height))
                show(pt)
            }
            override fun mouseExited(e: MouseEvent) {
                scheduleClose()
            }
        })
    }

    /**
     * 在指定位置显示气泡弹窗
     */
    fun show(point: RelativePoint) {
        // 如果已有弹窗正在显示，不重复创建
        if (popup?.isVisible == true) return

        // 气泡内鼠标进出监听：进入取消关闭计时，离开启动关闭计时
        val hoverListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = cancelClose()
            override fun mouseExited(e: MouseEvent) = scheduleClose()
        }

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(2)
            addMouseListener(hoverListener)

            add(createBalloonItem("添加 Command") {
                popup?.cancel()
                onAddCommand()
            }.also {
                it.maximumSize = Dimension(Int.MAX_VALUE, it.preferredSize.height)
                it.addMouseListener(hoverListener)
            })
            add(createBalloonItem("添加分割线") {
                popup?.cancel()
                onAddSeparator()
            }.also {
                it.maximumSize = Dimension(Int.MAX_VALUE, it.preferredSize.height)
                it.addMouseListener(hoverListener)
            })
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(false)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setBorderColor(java.awt.Color.GRAY)
            .createPopup()

        popup?.show(point)
    }

    /**
     * 从 ActionToolbar 的添加按钮位置显示
     */
    fun showFromActionButton(point: RelativePoint?) {
        if (point != null) {
            show(point)
        }
    }

    /**
     * 延迟关闭气泡弹窗（给用户从按钮移动到气泡的时间）
     */
    private fun scheduleClose() {
        closeTimer?.stop()
        closeTimer = javax.swing.Timer(300) {
            popup?.cancel()
        }.apply { isRepeats = false; start() }
    }

    /**
     * 取消气泡弹窗的延迟关闭
     */
    private fun cancelClose() {
        closeTimer?.stop()
    }

    /**
     * 创建气泡弹窗中的可点击项
     */
    private fun createBalloonItem(text: String, onClick: () -> Unit): JLabel {
        return JLabel(text).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(6, 12)
            isOpaque = true
            background = UIManager.getColor("Panel.background")
            alignmentX = 0.0f
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    onClick()
                }
                override fun mouseEntered(e: MouseEvent) {
                    background = UIManager.getColor("List.selectionBackground")
                    foreground = UIManager.getColor("List.selectionForeground")
                }
                override fun mouseExited(e: MouseEvent) {
                    background = UIManager.getColor("Panel.background")
                    foreground = UIManager.getColor("Label.foreground")
                }
            })
        }
    }

    companion object {
        /**
         * 清除 ActionButton 上的 tooltip（包括 IntelliJ 的 HelpTooltip 和 Swing 标准 tooltip）
         */
        fun suppressActionButtonTooltip(comp: JComponent) {
            comp.toolTipText = null
            javax.swing.ToolTipManager.sharedInstance().unregisterComponent(comp)
            com.intellij.ide.HelpTooltip.dispose(comp)
        }
    }
}
