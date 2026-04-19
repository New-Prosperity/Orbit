package me.nebula.orbit.utils.vanilla

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.CommandManager
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import me.nebula.gravity.translation.Keys

private fun moduleArg(name: String = "module") = ArgumentType.Word(name).apply {
    setSuggestionCallback { _, _, suggestion ->
        val prefix = suggestion.input.substringAfterLast(" ").lowercase()
        VanillaModules.registeredIds().filter { it.startsWith(prefix) }.forEach {
            suggestion.addEntry(SuggestionEntry(it))
        }
    }
}

private fun configKeyArg(name: String = "key") = ArgumentType.Word(name).apply {
    setSuggestionCallback { _, context, suggestion ->
        val moduleId = context.getRaw("module") ?: return@setSuggestionCallback
        val module = VanillaModules.get(moduleId) ?: return@setSuggestionCallback
        val prefix = suggestion.input.substringAfterLast(" ").lowercase()
        module.configParams.map { it.key }.filter { it.startsWith(prefix) }.forEach {
            suggestion.addEntry(SuggestionEntry(it))
        }
    }
}

fun installGameRuleCommand(commandManager: CommandManager) {
    commandManager.register(command("gamerule") {
        permission("nebula.admin")

        subCommand("list") {
            onPlayerExecute {
                val instance = player.instance ?: return@onPlayerExecute
                val enabled = VanillaModules.enabledFor(instance)
                val all = VanillaModules.all()

                player.sendMessage(player.translate(Keys.Orbit.Gamerule.Header))
                for (module in all) {
                    val status = if (enabled.containsKey(module.id)) "ON" else "OFF"
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.ListEntry, "status" to status, "module" to module.id, "description" to module.description))
                }
            }
        }

        subCommand("enable") {
            argument(moduleArg())
            onPlayerExecute {
                val id = requireArg("module") ?: return@onPlayerExecute
                val instance = player.instance ?: return@onPlayerExecute
                if (VanillaModules.get(id) == null) {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.UnknownModule, "module" to id))
                    return@onPlayerExecute
                }
                VanillaModules.enable(instance, id)
                player.sendMessage(player.translate(Keys.Orbit.Gamerule.Enabled, "module" to id))
            }
        }

        subCommand("disable") {
            argument(moduleArg())
            onPlayerExecute {
                val id = requireArg("module") ?: return@onPlayerExecute
                val instance = player.instance ?: return@onPlayerExecute
                if (!VanillaModules.isEnabled(instance, id)) {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.NotEnabled, "module" to id))
                    return@onPlayerExecute
                }
                VanillaModules.disable(instance, id)
                player.sendMessage(player.translate(Keys.Orbit.Gamerule.Disabled, "module" to id))
            }
        }

        subCommand("info") {
            argument(moduleArg())
            onPlayerExecute {
                val id = requireArg("module") ?: return@onPlayerExecute
                val instance = player.instance ?: return@onPlayerExecute
                val module = VanillaModules.get(id) ?: run {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.UnknownModule, "module" to id))
                    return@onPlayerExecute
                }
                val active = VanillaModules.getActive(instance, id)
                val status = if (active != null) "<green>ON" else "<red>OFF"

                player.sendMessage(player.translate(Keys.Orbit.Gamerule.InfoHeader, "module" to module.id))
                player.sendMessage(player.translate(Keys.Orbit.Gamerule.InfoDesc, "description" to module.description))
                player.sendMessage(player.translate(Keys.Orbit.Gamerule.Status, "status" to status))

                if (module.configParams.isNotEmpty()) {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.ConfigHeader))
                    for (param in module.configParams) {
                        val currentValue = active?.config?.get(param.key, param.default) ?: param.default
                        player.sendMessage(player.translate(Keys.Orbit.Gamerule.ConfigEntry, "key" to param.key, "value" to currentValue.toString(), "description" to param.description))
                    }
                }
            }
        }

        subCommand("set") {
            argument(moduleArg())
            argument(configKeyArg())
            wordArgument("value")
            onPlayerExecute {
                val id = requireArg("module") ?: return@onPlayerExecute
                val key = requireArg("key") ?: return@onPlayerExecute
                val rawValue = requireArg("value") ?: return@onPlayerExecute
                val instance = player.instance ?: return@onPlayerExecute

                val module = VanillaModules.get(id) ?: run {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.UnknownModule, "module" to id))
                    return@onPlayerExecute
                }

                val param = module.configParams.find { it.key == key } ?: run {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.UnknownKey, "key" to key, "module" to id))
                    return@onPlayerExecute
                }

                val parsed = parseConfigValue(param, rawValue) ?: run {
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.InvalidValue, "value" to rawValue, "key" to key))
                    return@onPlayerExecute
                }

                if (!VanillaModules.isEnabled(instance, id)) {
                    val config = ModuleConfig.fromDefaults(module)
                    config.set(key, parsed)
                    VanillaModules.enable(instance, id, config)
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.EnabledWith, "module" to id, "key" to key, "value" to parsed.toString()))
                } else {
                    VanillaModules.reconfigure(instance, id, key, parsed)
                    player.sendMessage(player.translate(Keys.Orbit.Gamerule.Updated, "module" to id, "key" to key, "value" to parsed.toString()))
                }
            }
        }
    })
}

@Suppress("UNCHECKED_CAST")
private fun parseConfigValue(param: ConfigParam<*>, input: String): Any? = when (param) {
    is ConfigParam.BoolParam -> param.parse(input)
    is ConfigParam.IntParam -> param.parse(input)
    is ConfigParam.DoubleParam -> param.parse(input)
}
