# ClojureScript Todo App — Design Spec

**Date:** 2026-04-26

## Overview

A minimal single-page todo app built with ClojureScript, Reagent, and shadow-cljs. Supports adding tasks, marking them complete, and deleting them. No persistence, no routing, no backend.

## Stack

- **ClojureScript** — language
- **Reagent** — React wrapper with atom-based state
- **shadow-cljs** — build tool, hot reload, npm integration
- **React / ReactDOM** — via npm, used by Reagent

## Architecture

All app state lives in a single `reagent.core/atom` — a vector of todo maps:

```clojure
[{:id 1 :text "Buy groceries" :done? false}
 {:id 2 :text "Read ClojureScript docs" :done? true}]
```

Components are pure functions of that atom. `swap!` is the only way state changes.

## Components

### `todo-input`
- Controlled text input with local atom for current input value
- "Add" button and Enter key both trigger add
- On add: appends `{:id (random-uuid) :text value :done? false}` to todos atom, clears input

### `todo-item`
- Renders one row: checkbox + task text + delete button
- Checkbox `on-change` → `swap!` toggles `:done?` for matching id
- Completed items: text struck through, row faded (via CSS class)
- Delete button → `swap!` filters out item by id

### `todo-app` (root)
- Renders `todo-input` at top
- Maps todos atom → list of `todo-item` components (keyed by id)
- Footer: count of items where `:done?` is false ("N items left")

## Data Flow

```
User types → local input atom
→ Add clicked / Enter pressed
→ swap! appends to todos atom
→ Reagent re-renders list

Checkbox clicked → swap! toggles :done? → re-render
Delete clicked   → swap! filters by id → re-render
```

## File Structure

```
cljs/
  shadow-cljs.edn         # build config: source paths, npm deps, dev server
  package.json            # npm: shadow-cljs, react, react-dom
  src/
    app/
      core.cljs           # all components + state + mount call
  public/
    index.html            # <div id="app"> + compiled JS script tag
    css/
      style.css           # minimal styles: layout, completed state, hover
```

## Styling

Minimal CSS — no framework. Key rules:
- Flex row for input + button
- `.done` class on completed task rows: `opacity: 0.5`, `text-decoration: line-through`
- Hover state on delete button

## What's Out of Scope

- Filtering (all/active/done)
- Local storage persistence
- Categories, priorities, due dates
- Routing or multiple views
