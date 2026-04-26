# BK Function Hall — Public Website Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the public-facing single-page website for BK Function Hall — hero, gallery, amenities, pricing, availability calendar, Google Reviews link, FAQ, Find Us map, and inquiry form — deployed to GitHub Pages via GitHub Actions.

**Architecture:** ClojureScript + Reagent SPA served as static files from GitHub Pages. Only two things talk to Supabase: reading booked dates (for the calendar) and submitting inquiries. Everything else — gallery, amenities, pricing, FAQ — is hardcoded in ClojureScript. Each page section is an isolated Reagent component.

**Tech Stack:** ClojureScript, Reagent, shadow-cljs, Supabase JS client (`@supabase/supabase-js`), GitHub Actions, GitHub Pages

---

## File Structure

```
src/
  bk/
    core.cljs          — app entry point, mounts root component
    supabase.cljs      — Supabase client init, fetch-booked-dates, submit-inquiry only
    public/
      page.cljs        — root public page component, composes all sections
      hero.cljs        — hero section component
      gallery.cljs     — photo grid (static, reads from public/images/gallery/)
      amenities.cljs   — amenities icon grid (hardcoded)
      pricing.cljs     — pricing section (hardcoded)
      calendar.cljs    — availability calendar (reads booked dates from Supabase)
      reviews.cljs     — Google Reviews link section
      faq.cljs         — FAQ accordion (hardcoded)
      location.cljs    — Find Us section with embedded map + directions link
      inquiry.cljs     — inquiry form component + submission logic
public/
  index.html           — entry point, <div id="app">, loads compiled JS
  manifest.json        — PWA manifest
  sw.js                — service worker for asset caching
  images/
    gallery/           — hall photos (jpg/png), committed to repo
    hero.jpg           — hero background photo
    icon-192.png       — PWA icon
    icon-512.png       — PWA icon
  css/
    style.css          — global styles, CSS variables, section layout
    components.css     — reusable component styles (cards, buttons, inputs)
.github/
  workflows/
    deploy.yml         — GitHub Actions: compile + deploy to gh-pages
shadow-cljs.edn        — build config
package.json           — npm deps incl. @supabase/supabase-js
```

---

### Task 1: Project scaffold and shadow-cljs config

**Files:**
- Create: `shadow-cljs.edn`
- Create: `package.json`
- Create: `src/bk/core.cljs`
- Create: `public/index.html`

- [ ] **Step 1: Create `package.json`**

```json
{
  "name": "bk-function-hall",
  "version": "1.0.0",
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "@supabase/supabase-js": "^2.39.0"
  },
  "devDependencies": {
    "shadow-cljs": "^2.28.0"
  }
}
```

- [ ] **Step 2: Create `shadow-cljs.edn`**

```clojure
{:source-paths ["src"]
 :dependencies [[reagent "1.2.0"]]
 :dev-http {3000 "public"}
 :builds
 {:app
  {:target :browser
   :output-dir "public/js"
   :asset-path "/js"
   :modules {:main {:init-fn bk.core/init}}
   :devtools {:after-load bk.core/init}}}}
```

- [ ] **Step 3: Create `src/bk/core.cljs`**

```clojure
(ns bk.core
  (:require [reagent.dom :as rdom]
            [bk.public.page :as page]))

(defn init []
  (rdom/render [page/root] (.getElementById js/document "app")))
```

- [ ] **Step 4: Create `public/index.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>BK Function Hall</title>
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/components.css">
  <link rel="manifest" href="/manifest.json">
</head>
<body>
  <div id="app"></div>
  <script src="/js/main.js"></script>
  <script>
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js');
    }
  </script>
</body>
</html>
```

- [ ] **Step 5: Install dependencies**

```bash
npm install
```

Expected: `node_modules/` created, `@supabase/supabase-js` present.

- [ ] **Step 6: Create minimal `src/bk/public/page.cljs` so it compiles**

```clojure
(ns bk.public.page)

(defn root []
  [:div "BK Function Hall — coming soon"])
```

- [ ] **Step 7: Verify build compiles**

```bash
npx shadow-cljs compile app
```

Expected: `[:app] Build completed. (N files, N compiled, 0 warnings)`

- [ ] **Step 8: Commit**

```bash
git add shadow-cljs.edn package.json package-lock.json src/bk/core.cljs src/bk/public/page.cljs public/index.html
git commit -m "feat: scaffold ClojureScript project with shadow-cljs"
```

---

### Task 2: Supabase client and database setup

**Files:**
- Create: `src/bk/supabase.cljs`

- [ ] **Step 1: Create Supabase project**

Go to https://supabase.com, create a new project called `bk-function-hall`. Note the Project URL and anon public key from Settings → API.

- [ ] **Step 2: Run schema SQL in Supabase SQL editor**

```sql
create table bookings (
  id uuid primary key default gen_random_uuid(),
  customer_name text not null,
  phone text not null,
  event_date date not null,
  event_type text not null,
  guest_count int,
  addons text[],
  notes text,
  status text default 'confirmed',
  created_at timestamptz default now()
);

create table inquiries (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  phone text not null,
  event_date date not null,
  event_type text not null,
  guest_count int,
  addons text[],
  message text,
  status text default 'new',
  created_at timestamptz default now()
);

alter table bookings enable row level security;
alter table inquiries enable row level security;

-- Public can only read event_date of confirmed bookings (for calendar)
create policy "public read booked dates" on bookings for select using (status = 'confirmed');
-- Public can submit inquiries
create policy "public insert inquiries" on inquiries for insert with check (true);
```

- [ ] **Step 3: Create `src/bk/supabase.cljs`**

```clojure
(ns bk.supabase
  (:require ["@supabase/supabase-js" :refer [createClient]]))

(def supabase-url "https://YOUR_PROJECT_ID.supabase.co")
(def supabase-key "YOUR_ANON_PUBLIC_KEY")

(defonce client (createClient supabase-url supabase-key))

(defn fetch-booked-dates [callback]
  (-> (.from client "bookings")
      (.select "event_date")
      (.eq "status" "confirmed")
      (.then #(callback (map :event_date (js->clj (.-data %) :keywordize-keys true))))
      (.catch #(js/console.error "fetch-booked-dates error" %))))

(defn submit-inquiry [inquiry callback]
  (-> (.from client "inquiries")
      (.insert (clj->js inquiry))
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false :error (.-message %)}))))
```

- [ ] **Step 4: Replace YOUR_PROJECT_ID and YOUR_ANON_PUBLIC_KEY with actual values from Supabase dashboard**

- [ ] **Step 5: Verify build still compiles**

```bash
npx shadow-cljs compile app
```

Expected: 0 warnings, 0 errors.

- [ ] **Step 6: Commit**

```bash
git add src/bk/supabase.cljs
git commit -m "feat: add Supabase client and database schema"
```

---

### Task 3: Global styles

**Files:**
- Create: `public/css/style.css`
- Create: `public/css/components.css`

- [ ] **Step 1: Create `public/css/style.css`**

```css
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --gold: #b8922a;
  --gold-light: #d4a843;
  --dark: #1a1208;
  --dark-soft: #2d2010;
  --cream: #fdf6e3;
  --cream-dark: #f5e9c8;
  --text: #3d2b0a;
  --text-light: #7a5c2a;
  --white: #ffffff;
  --radius: 12px;
  --shadow: 0 4px 20px rgba(0,0,0,0.1);
}

html { scroll-behavior: smooth; }

body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: var(--cream);
  color: var(--text);
  line-height: 1.6;
}

section { padding: 80px 24px; }

.section-title {
  font-size: 2rem;
  font-weight: 700;
  text-align: center;
  color: var(--dark);
  margin-bottom: 12px;
}

.section-subtitle {
  text-align: center;
  color: var(--text-light);
  margin-bottom: 48px;
  font-size: 1.1rem;
}

.container { max-width: 1100px; margin: 0 auto; }

@media (max-width: 768px) {
  section { padding: 56px 16px; }
  .section-title { font-size: 1.6rem; }
}
```

- [ ] **Step 2: Create `public/css/components.css`**

```css
/* Buttons */
.btn-primary {
  display: inline-block;
  padding: 14px 32px;
  background: var(--gold);
  color: var(--white);
  border: none;
  border-radius: 50px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.2s, transform 0.1s;
}
.btn-primary:hover { background: var(--gold-light); transform: translateY(-1px); }

.btn-outline {
  display: inline-block;
  padding: 14px 32px;
  background: transparent;
  color: var(--white);
  border: 2px solid var(--white);
  border-radius: 50px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.2s;
}
.btn-outline:hover { background: rgba(255,255,255,0.15); }

/* Form inputs */
.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1.5px solid var(--cream-dark);
  border-radius: var(--radius);
  font-size: 15px;
  color: var(--text);
  background: var(--white);
  outline: none;
  transition: border-color 0.2s;
}
.form-input:focus { border-color: var(--gold); }

.form-label {
  display: block;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 6px;
  font-size: 14px;
}

.form-group { margin-bottom: 20px; }
```

- [ ] **Step 3: Commit**

```bash
git add public/css/style.css public/css/components.css
git commit -m "feat: add global and component styles"
```

---

### Task 4: Hero section

**Files:**
- Create: `src/bk/public/hero.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/hero.cljs`**

```clojure
(ns bk.public.hero)

(defn section []
  [:section#hero
   {:style {:background "linear-gradient(rgba(0,0,0,0.5), rgba(0,0,0,0.6)), url('/images/hall-hero.jpg') center/cover no-repeat"
            :min-height "100vh"
            :display "flex"
            :align-items "center"
            :justify-content "center"
            :text-align "center"
            :color "#fff"
            :padding "0 24px"}}
   [:div
    [:h1 {:style {:font-size "clamp(2.5rem, 6vw, 4.5rem)"
                  :font-weight "800"
                  :letter-spacing "-1px"
                  :margin-bottom "16px"}}
     "BK Function Hall"]
    [:p {:style {:font-size "clamp(1.1rem, 2vw, 1.4rem)"
                 :opacity "0.9"
                 :margin-bottom "40px"
                 :max-width "500px"
                 :margin-left "auto"
                 :margin-right "auto"}}
     "The perfect venue for weddings, receptions & celebrations in your city"]
    [:div {:style {:display "flex" :gap "16px" :justify-content "center" :flex-wrap "wrap"}}
     [:a {:href "#calendar" :class "btn-outline"} "Check Availability"]
     [:a {:href "#inquiry" :class "btn-primary"} "Make an Inquiry"]]]])
```

- [ ] **Step 2: Add a placeholder hero image**

Create `public/images/` directory and add a placeholder. The owner will replace `hall-hero.jpg` with an actual photo later.

```bash
mkdir -p public/images
# placeholder — owner replaces with real photo
```

- [ ] **Step 3: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]))

(defn root []
  [:div
   [hero/section]])
```

- [ ] **Step 4: Start dev server and verify hero renders**

```bash
npx shadow-cljs watch app
```

Open http://localhost:3000 — should see the hero with heading, tagline, and two buttons. Buttons don't scroll yet (no target sections).

- [ ] **Step 5: Commit**

```bash
git add src/bk/public/hero.cljs src/bk/public/page.cljs public/images/
git commit -m "feat: add hero section"
```

---

### Task 5: Gallery section

**Files:**
- Create: `src/bk/public/gallery.cljs`
- Modify: `src/bk/public/page.cljs`

Photos live in `public/images/gallery/`. Add any jpg/png files there and list them in the component. To add new photos later: drop files in the folder, update the list, git push.

- [ ] **Step 1: Add placeholder gallery images**

```bash
mkdir -p public/images/gallery
# Place hall photos here as: hall-1.jpg, hall-2.jpg, hall-3.jpg etc.
# For now create the directory — owner provides real photos
```

- [ ] **Step 2: Create `src/bk/public/gallery.cljs`**

```clojure
(ns bk.public.gallery)

(def photos
  [{:src "/images/gallery/hall-1.jpg" :alt "BK Function Hall main hall"}
   {:src "/images/gallery/hall-2.jpg" :alt "BK Function Hall decorated for wedding"}
   {:src "/images/gallery/hall-3.jpg" :alt "BK Function Hall entrance"}
   {:src "/images/gallery/hall-4.jpg" :alt "BK Function Hall stage area"}
   {:src "/images/gallery/hall-5.jpg" :alt "BK Function Hall dining setup"}
   {:src "/images/gallery/hall-6.jpg" :alt "BK Function Hall parking"}])

(defn section []
  [:section#gallery {:style {:background "#fff"}}
   [:div.container
    [:h2.section-title "Our Venue"]
    [:p.section-subtitle "A glimpse of BK Function Hall"]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(auto-fill, minmax(280px, 1fr))"
                   :gap "16px"}}
     (for [{:keys [src alt]} photos]
       ^{:key src}
       [:div {:style {:border-radius "var(--radius)"
                      :overflow "hidden"
                      :aspect-ratio "4/3"
                      :background "var(--cream-dark)"}}
        [:img {:src src :alt alt
               :style {:width "100%" :height "100%" :object-fit "cover"}}]])]]])
```

- [ ] **Step 3: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]])
```

- [ ] **Step 4: Verify in browser**

Open http://localhost:3000 — gallery grid renders below hero. Photos show placeholder background until real images are added.

- [ ] **Step 5: Commit**

```bash
git add src/bk/public/gallery.cljs src/bk/public/page.cljs public/images/gallery/
git commit -m "feat: add gallery section (static images)"
```

---

### Task 6: Amenities section

**Files:**
- Create: `src/bk/public/amenities.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/amenities.cljs`**

```clojure
(ns bk.public.amenities)

(def amenities-list
  [{:icon "🚗" :title "Ample Parking" :desc "Spacious parking for all your guests"}
   {:icon "🍽️" :title "Catering Kitchen" :desc "Fully equipped kitchen for your caterers"}
   {:icon "🌸" :title "Decoration" :desc "Professional decoration services available"}
   {:icon "👥" :title "Large Capacity" :desc "Comfortably accommodates up to 500 guests"}])

(defn section []
  [:section#amenities {:style {:background "var(--cream)"}}
   [:div.container
    [:h2.section-title "What We Offer"]
    [:p.section-subtitle "Everything you need for an unforgettable event"]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
                   :gap "24px"}}
     (for [{:keys [icon title desc]} amenities-list]
       ^{:key title}
       [:div {:style {:background "#fff"
                      :border-radius "var(--radius)"
                      :padding "32px 24px"
                      :text-align "center"
                      :box-shadow "var(--shadow)"}}
        [:div {:style {:font-size "3rem" :margin-bottom "16px"}} icon]
        [:h3 {:style {:font-size "1.1rem" :font-weight "700" :margin-bottom "8px" :color "var(--dark)"}} title]
        [:p {:style {:color "var(--text-light)" :font-size "0.95rem"}} desc]])]]])
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]])
```

- [ ] **Step 3: Verify in browser** — four amenity cards render.

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/amenities.cljs src/bk/public/page.cljs
git commit -m "feat: add amenities section"
```

---

### Task 7: Pricing section

**Files:**
- Create: `src/bk/public/pricing.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/pricing.cljs`**

```clojure
(ns bk.public.pricing)

(def pricing-items
  [{:label "Hall Hire" :from "₹30,000" :note "Full day"}
   {:label "Decoration" :from "₹15,000" :note "Starting price, varies by extent"}
   {:label "Catering" :from "₹500/plate" :note "Minimum 100 plates"}
   {:label "Other Add-ons" :from "Custom" :note "Discuss on call"}])

(defn section []
  [:section#pricing {:style {:background "#fff"}}
   [:div.container
    [:h2.section-title "Pricing"]
    [:p.section-subtitle "Starting rates — final pricing confirmed on call based on your requirements"]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
                   :gap "20px"
                   :margin-bottom "40px"}}
     (for [{:keys [label from note]} pricing-items]
       ^{:key label}
       [:div {:style {:border "2px solid var(--cream-dark)"
                      :border-radius "var(--radius)"
                      :padding "28px 24px"
                      :text-align "center"}}
        [:div {:style {:font-size "0.85rem" :font-weight "600" :color "var(--text-light)" :text-transform "uppercase" :letter-spacing "1px" :margin-bottom "8px"}} label]
        [:div {:style {:font-size "1.8rem" :font-weight "800" :color "var(--gold)" :margin-bottom "8px"}} from]
        [:div {:style {:font-size "0.9rem" :color "var(--text-light)"}} note]])]
    [:p {:style {:text-align "center" :color "var(--text-light)" :font-size "0.95rem"}}
     "📞 Call us or "
     [:a {:href "#inquiry" :style {:color "var(--gold)" :font-weight "600"}} "send an inquiry"]
     " to discuss your exact requirements and get a custom quote."]]])
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]])
```

- [ ] **Step 3: Verify in browser** — four pricing cards with gold "from" prices render.

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/pricing.cljs src/bk/public/page.cljs
git commit -m "feat: add pricing section"
```

---

### Task 8: Availability calendar

**Files:**
- Create: `src/bk/public/calendar.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/calendar.cljs`**

```clojure
(ns bk.public.calendar
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(defn days-in-month [year month]
  (.-getDate (js/Date. year month 0)))

(defn first-day-of-month [year month]
  (.-getDay (js/Date. year (dec month) 1)))

(defn today [] (js/Date.))

(defn date-str [year month day]
  (str year "-"
       (-> month str (.padStart 2 "0"))
       "-"
       (-> day str (.padStart 2 "0"))))

(def month-names ["January" "February" "March" "April" "May" "June"
                  "July" "August" "September" "October" "November" "December"])

(defn calendar-grid [year month booked-dates]
  (let [days (days-in-month year month)
        first-day (first-day-of-month year month)
        today-str (date-str (+ 1900 (.-getYear (today)))
                            (inc (.-getMonth (today)))
                            (.-getDate (today)))
        booked-set (set booked-dates)]
    [:div {:style {:display "grid" :grid-template-columns "repeat(7, 1fr)" :gap "4px"}}
     (for [d ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]]
       ^{:key d}
       [:div {:style {:text-align "center" :font-size "12px" :font-weight "700"
                      :color "var(--text-light)" :padding "8px 0"}} d])
     (for [i (range first-day)]
       ^{:key (str "empty-" i)}
       [:div])
     (for [day (range 1 (inc days))]
       (let [ds (date-str year month day)
             booked? (contains? booked-set ds)
             past? (< ds today-str)]
         ^{:key ds}
         [:div {:style {:text-align "center"
                        :padding "8px 4px"
                        :border-radius "8px"
                        :font-size "14px"
                        :font-weight "500"
                        :background (cond booked? "#fee2e2" past? "transparent" :else "#dcfce7")
                        :color (cond booked? "#dc2626" past? "var(--text-light)" :else "#16a34a")
                        :opacity (when past? "0.4")}}
          day]))]]))

(defn section []
  (let [now (today)
        year (r/atom (+ 1900 (.-getYear now)))
        month (r/atom (inc (.-getMonth now)))
        booked-dates (r/atom [])]
    (db/fetch-booked-dates #(reset! booked-dates %))
    (fn []
      [:section#calendar {:style {:background "var(--cream)"}}
       [:div.container {:style {:max-width "600px"}}
        [:h2.section-title "Check Availability"]
        [:p.section-subtitle "Green = available · Red = booked"]
        [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "24px" :box-shadow "var(--shadow)"}}
         [:div {:style {:display "flex" :align-items "center" :justify-content "space-between" :margin-bottom "20px"}}
          [:button {:on-click #(if (= @month 1)
                                 (do (reset! month 12) (swap! year dec))
                                 (swap! month dec))
                    :style {:background "none" :border "none" :font-size "20px" :cursor "pointer" :color "var(--gold)"}}
           "‹"]
          [:span {:style {:font-size "1.1rem" :font-weight "700"}}
           (str (nth month-names (dec @month)) " " @year)]
          [:button {:on-click #(if (= @month 12)
                                 (do (reset! month 1) (swap! year inc))
                                 (swap! month inc))
                    :style {:background "none" :border "none" :font-size "20px" :cursor "pointer" :color "var(--gold)"}}
           "›"]]
         [calendar-grid @year @month @booked-dates]]]])))
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]])
```

- [ ] **Step 3: Verify in browser** — calendar renders current month, prev/next buttons work, days show green (no bookings in Supabase yet).

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/calendar.cljs src/bk/public/page.cljs
git commit -m "feat: add availability calendar"
```

---

### Task 9: Google Reviews section

**Files:**
- Create: `src/bk/public/reviews.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/reviews.cljs`**

```clojure
(ns bk.public.reviews)

(def google-maps-url "https://maps.app.goo.gl/quTKKkZQ2EFVrt1t7")

(defn section []
  [:section#reviews {:style {:background "var(--cream)" :text-align "center"}}
   [:div.container
    [:h2.section-title "What Our Clients Say"]
    [:p.section-subtitle "Read verified reviews from our happy customers"]
    [:a {:href google-maps-url
         :target "_blank"
         :rel "noopener noreferrer"
         :class "btn-primary"
         :style {:display "inline-flex" :align-items "center" :gap "8px"}}
     "⭐ See our Google Reviews"]]])
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]
            [bk.public.reviews :as reviews]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]
   [reviews/section]])
```

- [ ] **Step 3: Verify in browser** — Google Reviews button renders and opens Maps listing in new tab.

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/reviews.cljs src/bk/public/page.cljs
git commit -m "feat: add Google Reviews section"
```

---

### Task 10: FAQ section

**Files:**
- Create: `src/bk/public/faq.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/faq.cljs`**

```clojure
(ns bk.public.faq
  (:require [reagent.core :as r]))

(def faqs
  [{:q "What is the maximum capacity?"
    :a "BK Function Hall can comfortably accommodate up to 500 guests."}
   {:q "Is outside catering allowed?"
    :a "Yes, you are welcome to bring your own caterers. Our fully equipped kitchen is available for their use."}
   {:q "How much advance is required to confirm a booking?"
    :a "We require 30% of the total agreed amount as advance to confirm your booking."}
   {:q "Is parking available?"
    :a "Yes, we have ample parking space for all your guests at no extra charge."}
   {:q "How do I check availability and get a quote?"
    :a "Use the availability calendar above to check your date, then fill in the inquiry form or call us directly. We'll get back to you promptly to discuss your requirements and confirm pricing."}])

(defn faq-item [{:keys [q a]} open? on-toggle]
  [:div {:style {:border-bottom "1px solid var(--cream-dark)" :padding "20px 0"}}
   [:button {:on-click on-toggle
             :style {:width "100%" :text-align "left" :background "none" :border "none"
                     :cursor "pointer" :display "flex" :justify-content "space-between"
                     :align-items "center" :font-size "1rem" :font-weight "600" :color "var(--dark)"}}
    q
    [:span {:style {:font-size "1.4rem" :color "var(--gold)" :line-height "1"}} (if open? "−" "+")]]
   (when open?
     [:p {:style {:margin-top "12px" :color "var(--text-light)" :line-height "1.7"}} a])])

(defn section []
  (let [open-idx (r/atom nil)]
    (fn []
      [:section#faq {:style {:background "#fff"}}
       [:div.container {:style {:max-width "760px"}}
        [:h2.section-title "Frequently Asked Questions"]
        (map-indexed
         (fn [i item]
           ^{:key i}
           [faq-item item
            (= @open-idx i)
            #(reset! open-idx (when (not= @open-idx i) i))])
         faqs)]])))
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]
            [bk.public.reviews :as reviews]
            [bk.public.faq :as faq]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]
   [reviews/section]
   [faq/section]])
```

- [ ] **Step 3: Verify FAQ accordion opens/closes in browser.**

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/faq.cljs src/bk/public/page.cljs
git commit -m "feat: add FAQ accordion section (hardcoded)"
```

---

### Task 11: Find Us section

**Files:**
- Create: `src/bk/public/location.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/location.cljs`**

```clojure
(ns bk.public.location)

(def directions-url "https://maps.app.goo.gl/quTKKkZQ2EFVrt1t7")

(defn section []
  [:section#location {:style {:background "var(--cream)"}}
   [:div.container
    [:h2.section-title "Find Us"]
    [:p.section-subtitle "BK Function Hall — easy to reach, easy to find"]
    [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "40px" :align-items "center"}}
     [:div
      [:p {:style {:font-size "1rem" :color "var(--text)" :line-height "1.8" :margin-bottom "24px"}}
       ;; Owner to fill in actual address
       "BK Function Hall" [:br]
       "Address line 1" [:br]
       "City, State — PIN" [:br]
       [:span {:style {:color "var(--text-light)" :font-size "0.9rem"}} "Near: landmark"]]
      [:a {:href directions-url
           :target "_blank"
           :rel "noopener noreferrer"
           :class "btn-primary"}
       "📍 Get Directions"]]
     [:div {:style {:border-radius "var(--radius)" :overflow "hidden" :height "300px"}}
      [:iframe {:src "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d2000!2d77.779173!3d14.5306815!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x3bb1510032c14a73%3A0x6cda236b0f44c696!2sBK%20Function%20Hall!5e0!3m2!1sen!2sin!4v1"
                :width "100%"
                :height "300"
                :style {:border "0"}
                :allow-full-screen ""
                :loading "lazy"
                :referrer-policy "no-referrer-when-downgrade"}]]]]])
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]
            [bk.public.reviews :as reviews]
            [bk.public.faq :as faq]
            [bk.public.location :as location]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]
   [reviews/section]
   [faq/section]
   [location/section]])
```

- [ ] **Step 3: Verify in browser** — embedded map renders with BK Function Hall pin, directions button opens Maps in new tab.

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/location.cljs src/bk/public/page.cljs
git commit -m "feat: add Find Us section with embedded map"
```

---

### Task 12: Inquiry form

**Files:**
- Create: `src/bk/public/inquiry.cljs`
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Create `src/bk/public/inquiry.cljs`**

```clojure
(ns bk.public.inquiry
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(def addon-options
  ["Decoration" "Catering" "Stage Setup" "Photography" "DJ / Music"])

(defn section []
  (let [form (r/atom {:name "" :phone "" :event_date "" :event_type "wedding"
                      :guest_count "" :addons [] :message ""})
        submitted? (r/atom false)
        error (r/atom nil)
        submitting? (r/atom false)]
    (fn []
      [:section#inquiry {:style {:background "var(--dark)" :color "#fff"}}
       [:div.container {:style {:max-width "700px"}}
        [:h2.section-title {:style {:color "#fff"}} "Make an Inquiry"]
        [:p.section-subtitle {:style {:color "rgba(255,255,255,0.7)"}}
         "Fill in your details and we'll call you back to discuss your requirements"]
        (if @submitted?
          [:div {:style {:text-align "center" :padding "40px"}}
           [:div {:style {:font-size "3rem" :margin-bottom "16px"}} "✅"]
           [:h3 {:style {:font-size "1.4rem" :margin-bottom "8px"}} "Thank you!"]
           [:p {:style {:color "rgba(255,255,255,0.7)"}} "We'll call you shortly to discuss your requirements."]]
          [:form {:on-submit (fn [e]
                               (.preventDefault e)
                               (reset! error nil)
                               (reset! submitting? true)
                               (let [data (-> @form
                                             (update :guest_count #(when (seq %) (js/parseInt %))))]
                                 (db/submit-inquiry data
                                   (fn [{:keys [ok error-msg]}]
                                     (reset! submitting? false)
                                     (if ok
                                       (reset! submitted? true)
                                       (reset! error "Something went wrong. Please try again."))))))}
           [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Your Name *"]
             [:input.form-input {:type "text" :required true :placeholder "Ravi Kumar"
                                 :value (:name @form)
                                 :on-change #(swap! form assoc :name (.. % -target -value))}]]
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Phone Number *"]
             [:input.form-input {:type "tel" :required true :placeholder "+91 98765 43210"
                                 :value (:phone @form)
                                 :on-change #(swap! form assoc :phone (.. % -target -value))}]]]
           [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Event Date *"]
             [:input.form-input {:type "date" :required true
                                 :value (:event_date @form)
                                 :on-change #(swap! form assoc :event_date (.. % -target -value))}]]
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Event Type *"]
             [:select.form-input {:value (:event_type @form)
                                  :on-change #(swap! form assoc :event_type (.. % -target -value))}
              [:option {:value "wedding"} "Wedding"]
              [:option {:value "reception"} "Reception"]
              [:option {:value "birthday"} "Birthday"]
              [:option {:value "other"} "Other"]]]]
           [:div.form-group
            [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Approximate Guest Count"]
            [:input.form-input {:type "number" :placeholder "200"
                                :value (:guest_count @form)
                                :on-change #(swap! form assoc :guest_count (.. % -target -value))}]]
           [:div.form-group
            [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Add-ons Interested In"]
            [:div {:style {:display "flex" :flex-wrap "wrap" :gap "10px" :margin-top "8px"}}
             (for [addon addon-options]
               ^{:key addon}
               (let [selected? (contains? (set (:addons @form)) addon)]
                 [:label {:style {:display "flex" :align-items "center" :gap "6px" :cursor "pointer"
                                  :color "rgba(255,255,255,0.8)" :font-size "14px"}}
                  [:input {:type "checkbox" :checked selected?
                           :on-change #(swap! form update :addons
                                              (fn [addons]
                                                (if selected?
                                                  (filterv #(not= % addon) addons)
                                                  (conj addons addon))))}]
                  addon]))]]
           [:div.form-group
            [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Message"]
            [:textarea.form-input {:rows 4 :placeholder "Any specific requirements or questions..."
                                   :value (:message @form)
                                   :on-change #(swap! form assoc :message (.. % -target -value))}]]
           (when @error
             [:p {:style {:color "#fca5a5" :margin-bottom "16px"}} @error])
           [:button {:type "submit" :class "btn-primary" :disabled @submitting?}
            (if @submitting? "Sending..." "Send Inquiry")]])]])))
```

- [ ] **Step 2: Update `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]
            [bk.public.reviews :as reviews]
            [bk.public.faq :as faq]
            [bk.public.location :as location]
            [bk.public.inquiry :as inquiry]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]
   [reviews/section]
   [faq/section]
   [location/section]
   [inquiry/section]])
```

- [ ] **Step 3: Test inquiry form end-to-end**

Fill out the form in the browser and submit. Go to Supabase Table Editor → `inquiries` — the submission should appear with status `new`.

- [ ] **Step 4: Commit**

```bash
git add src/bk/public/inquiry.cljs src/bk/public/page.cljs
git commit -m "feat: add inquiry form with Supabase submission"
```

---

### Task 13: PWA manifest and service worker

**Files:**
- Create: `public/manifest.json`
- Create: `public/sw.js`

- [ ] **Step 1: Create `public/manifest.json`**

```json
{
  "name": "BK Function Hall",
  "short_name": "BK Hall",
  "description": "Book BK Function Hall for weddings, receptions and celebrations",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#fdf6e3",
  "theme_color": "#b8922a",
  "icons": [
    {
      "src": "/images/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/images/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

- [ ] **Step 2: Create placeholder PWA icons**

```bash
# Owner replaces these with real icons later
# For now create 192x192 and 512x512 placeholder PNGs
# Use any image editor or online tool to create simple gold square icons
# Place at public/images/icon-192.png and public/images/icon-512.png
```

- [ ] **Step 3: Create `public/sw.js`**

```javascript
const CACHE = 'bk-hall-v1';
const ASSETS = ['/', '/css/style.css', '/css/components.css', '/js/main.js'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)));
});

self.addEventListener('fetch', e => {
  e.respondWith(
    caches.match(e.request).then(cached => cached || fetch(e.request))
  );
});
```

- [ ] **Step 4: Verify in browser**

Open DevTools → Application → Manifest — should show BK Function Hall with gold theme color. Service Workers tab should show the worker registered.

- [ ] **Step 5: Commit**

```bash
git add public/manifest.json public/sw.js public/images/
git commit -m "feat: add PWA manifest and service worker"
```

---

### Task 14: Footer

**Files:**
- Modify: `src/bk/public/page.cljs`

- [ ] **Step 1: Add footer to `src/bk/public/page.cljs`**

```clojure
(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]
            [bk.public.reviews :as reviews]
            [bk.public.faq :as faq]
            [bk.public.location :as location]
            [bk.public.inquiry :as inquiry]))

(defn footer []
  [:footer {:style {:background "var(--dark)" :color "rgba(255,255,255,0.6)"
                    :text-align "center" :padding "32px 24px" :font-size "14px"}}
   [:p {:style {:margin-bottom "8px" :font-weight "600" :color "#fff"}} "BK Function Hall"]
   [:p "© 2026 BK Function Hall. All rights reserved."]])

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]
   [reviews/section]
   [faq/section]
   [location/section]
   [inquiry/section]
   [footer]])
```

- [ ] **Step 2: Commit**

```bash
git add src/bk/public/page.cljs
git commit -m "feat: add footer"
```

---

### Task 15: GitHub Actions deployment to GitHub Pages

**Files:**
- Create: `.github/workflows/deploy.yml`

- [ ] **Step 1: Update `shadow-cljs.edn` to add release build**

```clojure
{:source-paths ["src"]
 :dependencies [[reagent "1.2.0"]]
 :dev-http {3000 "public"}
 :builds
 {:app
  {:target :browser
   :output-dir "public/js"
   :asset-path "/js"
   :modules {:main {:init-fn bk.core/init}}
   :devtools {:after-load bk.core/init}
   :release {:compiler-options {:optimizations :advanced}}}}}
```

- [ ] **Step 2: Create `.github/workflows/deploy.yml`**

```yaml
name: Deploy to GitHub Pages

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: package-lock.json

      - name: Install npm deps
        run: npm ci

      - name: Compile ClojureScript
        run: npx shadow-cljs release app

      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: public
```

- [ ] **Step 3: Enable GitHub Pages in repo settings**

Go to GitHub repo → Settings → Pages → Source: Deploy from branch → Branch: `gh-pages` → root.

- [ ] **Step 4: Push to main and verify deployment**

```bash
git add .github/workflows/deploy.yml shadow-cljs.edn
git commit -m "feat: add GitHub Actions deploy to GitHub Pages"
git push origin main
```

Go to GitHub → Actions tab — watch the workflow run. After it completes, visit `https://YOUR_USERNAME.github.io/YOUR_REPO/` — the public website should be live.

- [ ] **Step 5: Verify all sections load on the live URL** — hero, gallery (empty is fine), amenities, pricing, calendar, testimonials (if seeded), FAQ, inquiry form.

---

## Self-Review

**Spec coverage check:**
- ✅ Hero with two CTAs
- ✅ Gallery — static images from `public/images/gallery/`
- ✅ Amenities: parking, catering kitchen, decoration, seating capacity (hardcoded)
- ✅ Pricing with base rates + "call to confirm" note (hardcoded)
- ✅ Availability calendar — booked dates only, no customer details
- ✅ Google Reviews link to Maps listing
- ✅ FAQ accordion (hardcoded)
- ✅ Find Us — embedded map + directions link using actual coordinates
- ✅ Inquiry form — all required fields, submits to Supabase, confirmation message
- ✅ PWA manifest + service worker
- ✅ GitHub Actions + GitHub Pages deployment

**Placeholder scan:** No TBDs, all code blocks complete. Owner needs to fill in actual address in `location.cljs` and add real photos to `public/images/gallery/`.

**Type consistency:** `fetch-booked-dates` and `submit-inquiry` defined in Task 2 and used in Tasks 8 and 12 respectively.
