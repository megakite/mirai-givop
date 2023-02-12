package icu.megakite.mirai.givop

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import net.mamoe.mirai.silkconverter.SilkConverter
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.MiraiExperimentalApi
import java.util.concurrent.CompletableFuture

@OptIn(MiraiExperimentalApi::class, DelicateCoroutinesApi::class)
fun convertAsync(er: ExternalResource): CompletableFuture<ExternalResource> =
    GlobalScope.future {
        return@future SilkConverter().convert(er)
    }
