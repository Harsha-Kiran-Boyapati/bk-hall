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
- Photos stored in `public/images/gallery/` in the repo — updated via git push + deploy

**3. Amenities**
- Icon grid showing: Parking, Catering Kitchen, Decoration, Seating Capacity
- Hardcoded in frontend — content is stable and doesn't need a database

**4. Pricing**
- Base rates displayed for: Hall hire, Decoration (starting price), Catering (starting price), other add-ons
- Note: "Final pricing confirmed on call based on your requirements"
- No negotiated/event-specific prices shown
- Hardcoded in frontend — changes rarely, requires deliberate update via code

**5. Availability Calendar**
- Shows blocked/booked dates only
- No customer names or event details visible
- Read from Supabase — only confirmed bookings are marked blocked

**6. Google Reviews**
- "⭐ See our reviews on Google" button linking to the hall's Google Maps listing
- Link: https://maps.app.goo.gl/quTKKkZQ2EFVrt1t7
- No embedded widget — fully trusted external source, zero maintenance

**7. FAQ**
- Common questions (capacity, parking, outside catering policy, advance required, etc.)
- Hardcoded in frontend — stable content, no database needed

**8. Find Us**
- Full address and nearby landmark
- Embedded Google Maps iframe (coordinates: 14.5306815, 77.779173 — no API key required)
- "Get Directions" button linking to: https://maps.app.goo.gl/quTKKkZQ2EFVrt1t7

**9. Inquiry Form**
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

### Screens

**Inquiries**
- List of all inquiries from public form: name, phone, event date, event type, guest count, add-ons interested, message
- Status: `new` → `called back` → `converted` / `not interested`
- Both roles can update status
- Owner can convert inquiry to booking directly from this screen — opens booking creation form pre-filled with inquiry details

**Create Booking (owner only)**
Single form capturing everything agreed on the call, submitted atomically:
- Customer details: name, phone, event date, event type, guest count, add-ons, notes
- Line items: one row per charge (label + amount) — e.g. "Venue hire ₹30,000", "Decoration ₹15,000", "Helpers — 8 workers ₹4,000", "Electricity charges ₹2,000"
- First payment: amount + date (advance)
All three — booking, line items, first payment — inserted together in one transaction.

**Bookings**
- List view: date, customer name, event type, guest count, status (`confirmed` / `completed` / `cancelled`)
- Booking detail — operational section (both roles):
  - Customer name, phone
  - Event date, event type
  - Guest count
  - Add-ons ordered (decoration extent, catering, others)
  - Special requirements / notes
- Booking detail — financial section (owner only):
  - Line items: all charges with labels and amounts
  - Derived total = sum of line items (no stored total field)
  - Payment history: list of payments with amounts and dates
  - Derived paid = sum of payments
  - Derived due = total − paid
  - Add line item button (for post-event additions e.g. extra helpers)
  - Add payment button (for balance and any partial payments)
  - Per-event expenses: what owner actually spent (label, amount, date, category)

**WhatsApp Confirmation (owner only)**
- Button on booking detail: "Send Confirmation via WhatsApp"
- Generates a `wa.me` link with pre-filled message containing:
  - Customer name, event date, event type, guest count
  - Line items with amounts
  - Total, advance paid, balance due
- Opens WhatsApp on owner's device with message ready to send
- Owner reviews and sends manually — no automation

**Expenses — Overhead (owner only)**
- Monthly recurring costs not tied to a specific event
- Fields: label (free-text e.g. "Electricity bill — April"), amount, month, category (electricity / maintenance / cleaning / labour / other)
- Listed separately from per-event expenses

**Monthly P&L (owner only)**
- Select month/year to view
- Income: sum of `booking_line_items` for completed events that month
- Event expenses: sum of `booking_expenses` for events that month
- Overhead: sum of `overhead_expenses` for that month
- Net profit = Income − Event expenses − Overhead
- Simple table, no charts

---

## Database Schema (Supabase / Postgres)

```
profiles          — id, user_id (auth.users), role (owner|staff), name
inquiries         — id, name, phone, event_date, event_type, guest_count, addons, message, status, created_at
bookings          — id, customer_name, phone, event_date, event_type, guest_count, addons, notes, status, created_at
booking_line_items — id, booking_id, label, amount  (revenue: what customer is charged — venue hire, decoration, helpers, electricity etc.)
booking_payments  — id, booking_id, amount, date, note  (each payment received — advance, partial, balance)
booking_expenses  — id, booking_id, label, amount, date, category  (cost: what owner actually spent — categories: electricity/labour/catering/decoration/other)
overhead_expenses — id, label, amount, month, year, category  (monthly costs: electricity/maintenance/cleaning/labour/other)
```

Derived (never stored):
- **total** = sum of `booking_line_items` for a booking
- **paid** = sum of `booking_payments` for a booking
- **due** = total − paid

Hardcoded in frontend (no DB): amenities, pricing, FAQ, gallery photos
External (no DB): testimonials → Google Reviews link

RLS policies ensure `booking_line_items`, `booking_payments`, `booking_expenses`, `overhead_expenses`, and P&L data are readable only by the `owner` role.

---

## Phased Delivery

**Phase 1 (this spec):** Public website + admin panel as described above
**Phase 2 (future):** Online self-service booking with payment, WhatsApp Business API notifications
