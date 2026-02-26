package com.github.ccbar.settings

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.ui.CCBarSettingsPanel
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import javax.swing.JComponent

/**
 * CCBar 设置页面入口
 * 注册到 Settings → Tools → CCBar
 */
class CCBarSettingsConfigurable : Configurable, Configurable.NoScroll {

    private var settingsPanel: CCBarSettingsPanel? = null

    override fun getDisplayName(): String = "CCBar"

    override fun createComponent(): JComponent {
        settingsPanel = CCBarSettingsPanel()
        return settingsPanel!!.createPanel()
    }

    override fun isModified(): Boolean {
        return settingsPanel?.isModified() ?: false
    }

    override fun apply() {
        // 验证数据
        val errors = settingsPanel?.validate() ?: emptyList()
        if (errors.isNotEmpty()) {
            throw ConfigurationException(errors.first())
        }

        settingsPanel?.apply()

        // 清除图标缓存，让新配置生效
        CCBarIcons.clearCache()
    }

    override fun reset() {
        settingsPanel?.reset()
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
