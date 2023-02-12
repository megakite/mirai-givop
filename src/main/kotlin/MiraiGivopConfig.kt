package icu.megakite.mirai.givop

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object MiraiGivopConfig : AutoSavePluginConfig("MiraiGivopConfig") {
    @ValueDescription("故事语音占比, 数值为0(无)和1(全部)之间的小数, 默认值为0.8")
    val storyVoiceProbability by value<Double>(0.8)
}
