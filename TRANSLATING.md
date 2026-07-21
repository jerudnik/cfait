# Guidelines for Translators

Thank you for helping translate Cfait :-)

When translating the application on Codeberg Translate / Weblate, please keep the following technical details in mind regarding "smart syntax" and search keywords (keys starting with `parser_` and `search_`):

## 1. Smart Syntax (`parser_*`)
These strings are used by the application to quickly add task metadata using the keyboard when creating tasks.
- **Multiple Options:** You can provide multiple synonyms separated by commas (e.g., `url:,lien:`). The parser will accept any of them.
- **No Spaces:** Do not put spaces after the commas (`a,b,c` is correct, `a, b, c` is wrong).
- **Keep Symbols First:** The app uses the *very first item* in the list to build the examples in the Help menu. Always keep symbols like `~`, `@`, or `@@` at the beginning of the list (e.g., `~,est:,dur:`).
- **Keep it Short:** Smart syntax is designed for fast typing. Stick to 2 or 3 common abbreviations.

## 2. Search Keywords (`search_is_*`)
These are the keywords users type in the search bar (like `is:done` or `is:ready`).
- You can translate the `is:` part to your language (e.g., `es:hecho`), or keep the English `is:` prefix if it feels more natural for technical users (e.g., `is:hecho`).
- You can provide multiple options separated by commas just like smart syntax (e.g., `is:nota,es:nota`).

## 3. Placeholders
Keep placeholders like `%{count}` or `%{error}` exactly as they are. They will be replaced by the application at runtime.
