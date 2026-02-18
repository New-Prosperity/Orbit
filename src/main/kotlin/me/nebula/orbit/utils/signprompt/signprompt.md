# SignPrompt

Open a sign editor as a text input prompt with callback on response.

## Usage

```kotlin
player.openSignPrompt(lines = listOf("", "", "", "Enter name:")) { player, lines ->
    val input = lines[0]
    player.sendMessage("You entered: $input")
}

SignPromptManager.cancel(player)
```

## Key API

- `Player.openSignPrompt(lines, callback)` — open a sign prompt with prefilled lines
- `SignPromptManager.prompt(player, lines, callback)` — register prompt and place sign
- `SignPromptManager.handleResponse(player, lines)` — process sign input and invoke callback
- `SignPromptManager.isPrompted(player)` — check if player has an active prompt
- `SignPromptManager.cancel(player)` — cancel active prompt
