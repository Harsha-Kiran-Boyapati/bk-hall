# BK Function Hall — Website Design Spec

**Date:** 2026-04-26

## Overview

A two-part web application for BK Function Hall:
1. **Public website** — informational single-page site for customers to learn about the hall and submit inquiries
2. **Admin panel** — booking management and full bookkeeping for owner and staff

Scale: ~3–4 events per month. No concurrency or performance concerns.

## Stack

- **Frontend:** ClojureScript + Reagent (single codebase for both public site and admin panel)
- **Backend:** Supabase (Postgres, Auth, Storage, Row Level Security)
- **Build:** shadow-cljs
- **PWA:** `manifest.json` + service worker for home screen install and asset caching. No offline writes — actions requiring Supabase need internet. Zero impact on users who don't install.
- **Hosting:** GitHub Pages (static frontend) + Supabase cloud (free tier)
- **CI/CD:** GitHub Actions — compiles ClojureScript on every push to main, deploys output to `gh-pages` branch

No separate backend service. All business logic either lives in the frontend or in Supabase RLS policies and Postgres functions.

---

## Part 1: Public Website

Single scrolling page at the root URL. No login required. Entirely read-only from the customer's perspective.

### Sections

**1. Hero**
- Full-width photo of the hall
- "BK Function Hall" heading + short tagline
- Two CTAs: "Check Availability" (smooth scroll to calendar) and "Make an Inquiry" (smooth scroll to form)

**2. Gallery**
- Photo grid of the hall interior, exterior, past decorated events
- Photos managed by owner via Supabase Storage (uploaded through admin panel)

**3. Amenities**
- Icon grid showing: Parking, Catering Kitchen, Decoration, Seating Capacity

**4. Pricing**
- Base rates displayed for: Hall hire, Decoration (starting price), Catering (starting price), other add-ons
- Note: "Final pricing confirmed on call based on your requirements"
- No negotiated/event-specific prices shown

**5. Availability Calendar**
- Shows blocked/booked dates only
- No customer names or event details visible
- Read from Supabase — only confirmed bookings are marked blocked

**6. Testimonials**
- Quotes from past events, managed by owner in admin panel

**7. FAQ**
- Common questions (capacity, parking, outside catering policy, etc.)
- Static content, editable by owner in admin panel

**8. Inquiry Form**
Fields: Name, Phone number, Event date, Event type (wedding/reception/birthday/other), Approximate guest count, Add-ons interested in (checkboxes), Message
- Submits to `inquiries` table in Supabase
- Customer sees a confirmation message: "We'll call you shortly to discuss your requirements"
- No WhatsApp button — customer calls or messages the owner directly

---

## Part 2: Admin Panel

Accessible at `/admin`. Requires login. Built as a separate view in the same ClojureScript app.

### Authentication

- Supabase Auth with email + password
- Owner creates staff accounts manually via Supabase dashboard
- Role stored in `profiles` table (`owner` or `staff`)
- Supabase RLS policies enforce role-based data access
- Sessions persist (remember me) — rarely need to re-login

### Roles

| Feature | Owner | Staff |
|---|---|---|
| View bookings | ✅ | ✅ |
| View booking requirements (add-ons, guest count, notes) | ✅ | ✅ |
| View/edit financial details (prices, payments, balance) | ✅ | ❌ |
| Add/edit expenses | ✅ | ❌ |
| View P&L | ✅ | ❌ |
| View inquiries | ✅ | ✅ |
| Mark inquiry status | ✅ | ✅ |
| Send WhatsApp confirmation | ✅ | ❌ |
| Manage gallery/testimonials/FAQ | ✅ | ❌ |

### Screens

**Inquiries**
- List of all inquiries from public form: name, phone, event date, event type, guest count, add-ons interested, message
- Status: `new` → `called back` → `converted` / `not interested`
- Both roles can update status
- Owner can convert inquiry to booking directly from this screen

**Bookings**
- List view: date, customer name, event type, guest count, status (`confirmed` / `completed` / `cancelled`)
- Booking detail — operational section (both roles):
  - Customer name, phone
  - Event date, event type
  - Guest count
  - Add-ons ordered (decoration extent, catering, others)
  - Special requirements / notes
- Booking detail — financial section (owner only):
  - Line items: venue hire fee, decoration amount, catering amount, other add-ons with custom labels and amounts
  - Discount applied (if any), final agreed total
  - Advance paid (amount + date)
  - Balance due + expected payment date
  - Per-event expenses: staff wages, vendor payments, materials (each with label, amount, date)

**WhatsApp Confirmation (owner only)**
- Button on booking detail: "Send Confirmation via WhatsApp"
- Generates a `wa.me` link with pre-filled message containing:
  - Customer name, event date, event type, guest count
  - Add-ons confirmed
  - Total agreed price, advance paid, balance due
- Opens WhatsApp on owner's device with message ready to send
- Owner reviews and sends manually — no automation

**Expenses — Overhead (owner only)**
- Monthly recurring costs not tied to a specific event
- Fields: label, amount, month, category (electricity / maintenance / cleaning / other)
- Listed separately from per-event expenses

**Monthly P&L (owner only)**
- Select month/year to view
- Income: sum of total agreed prices for completed events that month
- Event expenses: sum of all per-event expense line items for that month
- Overhead expenses: sum of overhead entries for that month
- Net profit = Income − Event expenses − Overhead
- Simple table, no charts

**Content Management (owner only)**
- Gallery: upload/delete photos (stored in Supabase Storage)
- Testimonials: add/edit/delete quotes
- FAQ: add/edit/delete Q&A pairs

---

## Database Schema (Supabase / Postgres)

```
profiles          — id, user_id (auth.users), role (owner|staff), name
inquiries         — id, name, phone, event_date, event_type, guest_count, addons, message, status, created_at
bookings          — id, customer_name, phone, event_date, event_type, guest_count, addons, notes, status
booking_financials — id, booking_id, venue_fee, discount, total_agreed, advance_paid, advance_date, balance_due
booking_line_items — id, booking_id, label, amount  (revenue side: agreed price breakdown shown to customer)
booking_expenses  — id, booking_id, label, amount, date, category  (cost side: what owner actually spent on the event)
overhead_expenses — id, label, amount, month, year, category
gallery_photos    — id, url, caption, order
testimonials      — id, quote, event_type, created_at
faq               — id, question, answer, order
```

RLS policies ensure `booking_financials`, `booking_expenses`, `overhead_expenses`, and P&L data are readable only by the `owner` role.

---

## Phased Delivery

**Phase 1 (this spec):** Public website + admin panel as described above
**Phase 2 (future):** Online self-service booking with payment, WhatsApp Business API notifications
```
