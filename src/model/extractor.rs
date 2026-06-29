// SPDX-License-Identifier: GPL-3.0-or-later
// File: ./src/model/extractor.rs
use std::collections::HashMap;
use uuid::Uuid;

#[derive(Debug)]
pub struct ExtractedTask {
    pub uid: String,
    pub parsed_existing_uid: Option<String>, // Found via <!-- uid:... -->
    pub parent_uid: Option<String>,
    pub dependencies: Vec<String>,
    pub raw_text: String,
    pub description: String,
    pub status: crate::model::TaskStatus,
    pub percent_complete: Option<u8>,
}

fn parse_checkbox(s: &str) -> Option<(crate::model::TaskStatus, Option<u8>, &str)> {
    if s.len() < 4 || !s.starts_with('[') {
        return None;
    }
    let mut chars = s.chars();
    chars.next(); // '['
    let inner = chars.next()?;
    if chars.next()? != ']' || chars.next()? != ' ' {
        return None;
    }
    let rest = chars.as_str();
    match inner {
        ' ' => Some((crate::model::TaskStatus::NeedsAction, None, rest)),
        'x' | 'X' | '*' => Some((crate::model::TaskStatus::Completed, Some(100), rest)),
        '/' => Some((crate::model::TaskStatus::InProcess, None, rest)),
        '<' | '>' => Some((crate::model::TaskStatus::NeedsAction, Some(50), rest)),
        '-' | '~' => Some((crate::model::TaskStatus::Cancelled, None, rest)),
        _ => None,
    }
}

fn extract_uid_tag(line: &str) -> (String, Option<String>) {
    if let Some(idx) = line.rfind("<!-- uid:")
        && let Some(end_idx) = line[idx..].find("-->")
    {
        let uid = line[idx + 9..idx + end_idx].trim().to_string();
        let clean_line = line[..idx].trim().to_string();
        return (clean_line, Some(uid));
    }
    (line.trim_end().to_string(), None)
}

pub fn extract_list_prefix(line: &str) -> String {
    let mut prefix = String::new();
    let mut byte_offset = 0;
    let chars = line.chars();

    // Extract leading whitespace
    for c in chars {
        if c == ' ' || c == '\t' {
            prefix.push(c);
            byte_offset += c.len_utf8();
        } else {
            break;
        }
    }

    let rest = &line[byte_offset..];
    if rest.starts_with("- [ ] ")
        || rest.starts_with("- [x] ")
        || rest.starts_with("- [X] ")
        || rest.starts_with("- [/] ")
        || rest.starts_with("- [-] ")
        || rest.starts_with("- [<] ")
        || rest.starts_with("- [>] ")
    {
        prefix.push_str("- [ ] ");
    } else if rest.starts_with("* [ ] ")
        || rest.starts_with("* [x] ")
        || rest.starts_with("* [X] ")
        || rest.starts_with("* [/] ")
        || rest.starts_with("* [-] ")
        || rest.starts_with("* [<] ")
        || rest.starts_with("* [>] ")
    {
        prefix.push_str("* [ ] ");
    } else if rest.starts_with("- ") {
        prefix.push_str("- ");
    } else if rest.starts_with("* ") {
        prefix.push_str("* ");
    } else {
        let mut digit_bytes = 0;
        for c in rest.chars() {
            if c.is_ascii_digit() {
                digit_bytes += c.len_utf8();
            } else {
                break;
            }
        }
        if digit_bytes > 0 {
            let after = &rest[digit_bytes..];
            if after.starts_with(". [ ] ")
                || after.starts_with(". [x] ")
                || after.starts_with(". [X] ")
                || after.starts_with(". [/] ")
                || after.starts_with(". [-] ")
                || after.starts_with(". [<] ")
                || after.starts_with(". [>] ")
            {
                let num_str = &rest[..digit_bytes];
                let num: usize = num_str.parse().unwrap_or(1);
                prefix.push_str(&format!("{}. [ ] ", num + 1));
            } else if after.starts_with(". ") {
                let num_str = &rest[..digit_bytes];
                let num: usize = num_str.parse().unwrap_or(1);
                prefix.push_str(&format!("{}. ", num + 1));
            }
        }
    }
    prefix
}

pub fn has_extractable_subtasks(input: &str) -> bool {
    for line in input.lines() {
        let mut byte_offset = 0;
        for c in line.chars() {
            if c == ' ' || c == '\t' {
                byte_offset += c.len_utf8();
            } else {
                break;
            }
        }
        let rest = &line[byte_offset..];

        // Check for headers
        if rest.starts_with("# ") || rest.starts_with("## ") || rest.starts_with("### ") {
            return true;
        }

        if rest.starts_with("- ") || rest.starts_with("* ") || rest.starts_with("+ ") {
            let after_marker = &rest[2..];
            if parse_checkbox(after_marker).is_some() {
                return true;
            }
        } else {
            let mut digit_bytes = 0;
            for c in rest.chars() {
                if c.is_ascii_digit() {
                    digit_bytes += c.len_utf8();
                } else {
                    break;
                }
            }
            if digit_bytes > 0 && rest[digit_bytes..].starts_with(". ") {
                let after_marker = &rest[digit_bytes + 2..];
                if parse_checkbox(after_marker).is_some() {
                    return true;
                }
            }
        }
    }
    false
}

/// Takes a raw markdown string.
/// Returns (Cleaned Root Description, List of Extracted Subtasks).
pub fn extract_markdown_tasks(input: &str) -> (String, Vec<ExtractedTask>) {
    let mut cleaned_root_desc = String::new();
    let mut extracted: Vec<ExtractedTask> = Vec::new();

    // Stack stores (indent_level, task_uid)
    let mut indent_stack: Vec<(usize, String)> = Vec::new();
    // Map stores indent_level -> uid of last numbered task
    let mut last_numbered_at_indent: HashMap<usize, String> = HashMap::new();

    let mut active_task_idx: Option<usize> = None;

    for line in input.lines() {
        let mut indent = 0;
        let mut byte_offset = 0;
        for c in line.chars() {
            if c == ' ' {
                indent += 1;
                byte_offset += c.len_utf8();
            } else if c == '\t' {
                indent += 4;
                byte_offset += c.len_utf8();
            } else {
                break;
            }
        }

        let rest = &line[byte_offset..];

        if rest.is_empty() {
            // Empty line: append to active task if exists, else root
            if let Some(idx) = active_task_idx {
                extracted[idx].description.push('\n');
            } else {
                cleaned_root_desc.push('\n');
            }
            continue;
        }

        // Check if it's a valid Markdown task list
        let mut is_task = false;
        let mut is_numbered = false;
        let mut parsed_status = crate::model::TaskStatus::NeedsAction;
        let mut parsed_pc = None;
        let mut raw_text = "";
        let mut header_depth = 0;

        if let Some(stripped) = rest.strip_prefix("# ") {
            is_task = true;
            header_depth = 1;
            raw_text = stripped;
        } else if let Some(stripped) = rest.strip_prefix("## ") {
            is_task = true;
            header_depth = 2;
            raw_text = stripped;
        } else if let Some(stripped) = rest.strip_prefix("### ") {
            is_task = true;
            header_depth = 3;
            raw_text = stripped;
        }

        if is_task {
            if let Some((status, pc, r)) = parse_checkbox(raw_text) {
                parsed_status = status;
                parsed_pc = pc;
                raw_text = r;
            }
        } else if rest.starts_with("- ") || rest.starts_with("* ") || rest.starts_with("+ ") {
            let after_marker = &rest[2..];
            if let Some((status, pc, r)) = parse_checkbox(after_marker) {
                is_task = true;
                parsed_status = status;
                parsed_pc = pc;
                raw_text = r;
            }
        } else {
            // Check for numbered lists (e.g., "1. [ ] ")
            let mut digit_bytes = 0;
            for c in rest.chars() {
                if c.is_ascii_digit() {
                    digit_bytes += c.len_utf8();
                } else {
                    break;
                }
            }
            if digit_bytes > 0 && rest[digit_bytes..].starts_with(". ") {
                let after_marker = &rest[digit_bytes + 2..];
                if let Some((status, pc, r)) = parse_checkbox(after_marker) {
                    is_task = true;
                    is_numbered = true;
                    parsed_status = status;
                    parsed_pc = pc;
                    raw_text = r;
                }
            }
        }

        if is_task {
            let (clean_text, parsed_uid) = extract_uid_tag(raw_text);
            let uid = parsed_uid
                .clone()
                .unwrap_or_else(|| Uuid::new_v4().to_string());

            // For headers, depth is absolute. For lists, it's relative to indentation.
            let effective_indent = if header_depth > 0 {
                (header_depth - 1) * 4 // Treat H1 as 0, H2 as 4, H3 as 8
            } else {
                indent
            };

            // Pop stack until we find a parent that has a strictly smaller indentation
            while let Some(&(stack_indent, _)) = indent_stack.last() {
                if stack_indent >= effective_indent {
                    indent_stack.pop();
                } else {
                    break;
                }
            }
            let parent_uid = indent_stack.last().map(|(_, id)| id.clone());

            // Determine dependencies using the last numbered task at THIS indentation level
            let mut dependencies = Vec::new();
            if is_numbered {
                if let Some(dep_uid) = last_numbered_at_indent.get(&effective_indent) {
                    dependencies.push(dep_uid.clone());
                }
                last_numbered_at_indent.insert(effective_indent, uid.clone());
            } else {
                // Breaking the numbered chain
                last_numbered_at_indent.remove(&effective_indent);
            }

            // Push ourselves to the stack to become a potential parent for the next lines
            indent_stack.push((effective_indent, uid.clone()));

            extracted.push(ExtractedTask {
                uid,
                parsed_existing_uid: parsed_uid,
                parent_uid,
                dependencies,
                raw_text: clean_text,
                description: String::new(),
                status: parsed_status,
                percent_complete: parsed_pc,
            });
            active_task_idx = Some(extracted.len() - 1);
        } else {
            // Not a task line. Append it to the relevant description.
            if indent == 0 || header_depth > 0 {
                // Indent 0 or headers break the list completely. Back to root parent notes.
                active_task_idx = None;
                indent_stack.clear();
                last_numbered_at_indent.clear();

                if !cleaned_root_desc.is_empty() && !cleaned_root_desc.ends_with('\n') {
                    cleaned_root_desc.push('\n');
                }
                cleaned_root_desc.push_str(rest);
                cleaned_root_desc.push('\n');
            } else if let Some(idx) = active_task_idx {
                // Belongs to the active subtask's notes
                if !extracted[idx].description.is_empty()
                    && !extracted[idx].description.ends_with('\n')
                {
                    extracted[idx].description.push('\n');
                }
                extracted[idx].description.push_str(rest);
                extracted[idx].description.push('\n');
            } else {
                // Indented, but no active task -> Belongs to root parent
                if !cleaned_root_desc.is_empty() && !cleaned_root_desc.ends_with('\n') {
                    cleaned_root_desc.push('\n');
                }
                cleaned_root_desc.push_str(rest);
                cleaned_root_desc.push('\n');
            }
        }
    }

    // Clean up trailing newlines
    let cleaned_root_desc = cleaned_root_desc.trim_end().to_string();
    for task in &mut extracted {
        task.description = task.description.trim_end().to_string();
    }

    (cleaned_root_desc, extracted)
}

pub fn serialize_task_tree(store: &crate::store::TaskStore, root_uid: &str) -> String {
    let mut out = String::new();
    let root = if let Some(r) = store.get_task_ref(root_uid) {
        r
    } else {
        return out;
    };

    let mut children_map: std::collections::HashMap<String, Vec<&crate::model::Task>> =
        std::collections::HashMap::new();
    for map in store.calendars.values() {
        for t in map.values() {
            if let Some(p) = &t.parent_uid {
                children_map.entry(p.clone()).or_default().push(t);
            }
        }
    }

    for list in children_map.values_mut() {
        list.sort_by(|a, b| {
            a.compare_for_sort(b, 5, false, crate::config::SortPreset::UrgentStartedDue)
        });
    }

    if !root.description.is_empty() {
        out.push_str(&root.description);
        out.push('\n');
        out.push('\n');
    }

    fn serialize_node(
        task: &crate::model::Task,
        children_map: &std::collections::HashMap<String, Vec<&crate::model::Task>>,
        depth: usize,
        out: &mut String,
    ) {
        let status_box = match task.status {
            crate::model::TaskStatus::NeedsAction => {
                if task.is_paused() {
                    "[<]"
                } else {
                    "[ ]"
                }
            }
            crate::model::TaskStatus::InProcess => "[/]",
            crate::model::TaskStatus::Completed => "[x]",
            crate::model::TaskStatus::Cancelled => "[-]",
        };
        let smart_string = task.to_smart_string();
        let uid_tag = format!("<!-- uid:{} -->", task.uid);
        let indent = "    ".repeat(depth - 1);

        out.push_str(&format!(
            "{}- {} {} {}\n",
            indent, status_box, smart_string, uid_tag
        ));

        if !task.description.is_empty() {
            for line in task.description.lines() {
                out.push_str(&format!("{}  {}\n", indent, line));
            }
        }

        if let Some(children) = children_map.get(&task.uid) {
            for child in children {
                serialize_node(child, children_map, depth + 1, out);
            }
        }
    }

    if let Some(children) = children_map.get(root_uid) {
        for child in children {
            serialize_node(child, &children_map, 1, &mut out);
        }
    }

    out.trim_end().to_string()
}
