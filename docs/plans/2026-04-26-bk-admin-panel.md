# BK Function Hall — Admin Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the admin panel at `/admin` — login, inquiries management, booking creation and detail, financials (owner only), overhead expenses, and monthly P&L.

**Architecture:** Same ClojureScript + Reagent SPA. `core.cljs` detects the URL path and renders either the public page or the admin panel. Supabase Auth handles login; the user's role (`owner` or `staff`) is read from the `profiles` table after login and stored in a top-level atom. All admin Supabase calls are authenticated (JWT sent automatically by the client). RLS policies enforce owner-only access to financial data server-side.

**Tech Stack:** ClojureScript, Reagent, shadow-cljs, `@supabase/supabase-js` (auth + data), Supabase RLS

---

## File Structure

```
src/bk/
  core.cljs                    — modify: route between public and admin by URL path
  supabase.cljs                — modify: add auth functions + all admin DB functions
  admin/
    state.cljs                 — top-level atoms: session, role, current screen, screen params
    shell.cljs                 — admin layout: nav sidebar/topbar + screen router
    login.cljs                 — login form component
    inquiries.cljs             — inquiries list + status update
    bookings.cljs              — bookings list
    booking_form.cljs          — create booking form (owner only)
    booking_detail.cljs        — booking detail: operational + financial sections
    overhead.cljs              — overhead expenses list + add form
    pnl.cljs                   — monthly P&L view
supabase/
  admin_rls.sql                — RLS policies for authenticated access + owner-only tables
```

---

### Task 1: Admin RLS policies

**Files:**
- Create: `supabase/admin_rls.sql`

- [ ] **Step 1: Create `supabase/admin_rls.sql`**

```sql
-- Helper: get role of the current authenticated user
create or replace function current_user_role()
returns text as $$
  select role from profiles where user_id = auth.uid()
$$ language sql security definer stable;

-- PROFILES: authenticated users can read their own profile
create policy "user reads own profile" on profiles
  for select to authenticated
  using (user_id = auth.uid());

-- BOOKINGS: both roles can read and insert; only owner can update/delete
create policy "authenticated read bookings" on bookings
  for select to authenticated using (true);

create policy "owner insert bookings" on bookings
  for insert to authenticated
  with check (current_user_role() = 'owner');

create policy "owner update bookings" on bookings
  for update to authenticated
  using (current_user_role() = 'owner');

-- INQUIRIES: both roles can read and update status
create policy "authenticated read inquiries" on inquiries
  for select to authenticated using (true);

create policy "authenticated update inquiry status" on inquiries
  for update to authenticated
  using (true)
  with check (true);

-- BOOKING_LINE_ITEMS: owner only
create policy "owner all booking_line_items" on booking_line_items
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- BOOKING_PAYMENTS: owner only
create policy "owner all booking_payments" on booking_payments
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- BOOKING_EXPENSES: owner only
create policy "owner all booking_expenses" on booking_expenses
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- OVERHEAD_EXPENSES: owner only
create policy "owner all overhead_expenses" on overhead_expenses
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');
```

- [ ] **Step 2: Run in Supabase SQL Editor**

Go to Supabase → SQL Editor → New query, paste the full contents of `supabase/admin_rls.sql` and run it.

- [ ] **Step 3: Commit**

```bash
git add supabase/admin_rls.sql
git commit -m "chore: add admin RLS policies"
```

---

### Task 2: Admin state atom and Supabase auth functions

**Files:**
- Create: `src/bk/admin/state.cljs`
- Modify: `src/bk/supabase.cljs`

- [ ] **Step 1: Create `src/bk/admin/state.cljs`**

```clojure
(ns bk.admin.state
  (:require [reagent.core :as r]))

(defonce session (r/atom nil))   ; Supabase session object or nil
(defonce role (r/atom nil))      ; "owner" | "staff" | nil
(defonce screen (r/atom :login)) ; current admin screen keyword
(defonce screen-params (r/atom {})) ; params for current screen (e.g. {:booking-id "..."})
```

- [ ] **Step 2: Add auth and profile functions to `src/bk/supabase.cljs`**

```clojure
(ns bk.supabase
  (:require ["@supabase/supabase-js" :refer [createClient]]))

(goog-define SUPABASE_URL "")
(goog-define SUPABASE_KEY "")

(defonce client (createClient SUPABASE_URL SUPABASE_KEY))

(defn fetch-booked-dates [callback]
  (-> (.from client "public_booked_dates")
      (.select "event_date")
      (.then #(callback (map :event_date (js->clj (.-data %) :keywordize-keys true))))
      (.catch #(js/console.error "fetch-booked-dates error" %))))

(defn submit-inquiry [inquiry callback]
  (-> (.from client "inquiries")
      (.insert (clj->js inquiry))
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false :error (.-message %)}))))

;; Auth
(defn sign-in [email password callback]
  (-> (.-auth client)
      (.signInWithPassword (clj->js {:email email :password password}))
      (.then #(let [err (.-error %)]
                (if err
                  (callback {:ok false :error (.-message err)})
                  (callback {:ok true :session (.-session %)}))))
      (.catch #(callback {:ok false :error (.-message %)}))))

(defn sign-out [callback]
  (-> (.-auth client)
      (.signOut)
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

(defn get-session [callback]
  (-> (.-auth client)
      (.getSession)
      (.then #(callback (.. % -data -session)))
      (.catch #(callback nil))))

(defn on-auth-change [callback]
  (-> (.-auth client)
      (.onAuthStateChange (fn [_event session] (callback session)))))

;; Profile
(defn fetch-role [callback]
  (-> (.from client "profiles")
      (.select "role")
      (.eq "user_id" (.. client -auth -currentUser -id))
      (.single)
      (.then #(callback (get (js->clj (.-data %) :keywordize-keys true) :role)))
      (.catch #(callback nil))))

;; Inquiries
(defn fetch-inquiries [callback]
  (-> (.from client "inquiries")
      (.select "*")
      (.order "created_at" (clj->js {:ascending false}))
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-inquiries error" %))))

(defn update-inquiry-status [id status callback]
  (-> (.from client "inquiries")
      (.update (clj->js {:status status}))
      (.eq "id" id)
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

;; Bookings
(defn fetch-bookings [callback]
  (-> (.from client "bookings")
      (.select "*")
      (.order "event_date" (clj->js {:ascending false}))
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-bookings error" %))))

(defn fetch-booking [id callback]
  (-> (.from client "bookings")
      (.select "*")
      (.eq "id" id)
      (.single)
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-booking error" %))))

(defn update-booking-status [id status callback]
  (-> (.from client "bookings")
      (.update (clj->js {:status status}))
      (.eq "id" id)
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

;; Create booking atomically: booking + line items + first payment in one transaction
(defn create-booking [booking line-items first-payment callback]
  (-> (.-rpc client)
      (.call "create_booking_with_items"
             (clj->js {:p_booking booking
                       :p_line_items line-items
                       :p_payment first-payment}))
      (.then #(if (.-error %)
                (callback {:ok false :error (.. % -error -message)})
                (callback {:ok true :id (.. % -data)})))
      (.catch #(callback {:ok false :error (.-message %)}))))

;; Line items
(defn fetch-line-items [booking-id callback]
  (-> (.from client "booking_line_items")
      (.select "*")
      (.eq "booking_id" booking-id)
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-line-items error" %))))

(defn add-line-item [item callback]
  (-> (.from client "booking_line_items")
      (.insert (clj->js item))
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

;; Payments
(defn fetch-payments [booking-id callback]
  (-> (.from client "booking_payments")
      (.select "*")
      (.eq "booking_id" booking-id)
      (.order "date" (clj->js {:ascending true}))
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-payments error" %))))

(defn add-payment [payment callback]
  (-> (.from client "booking_payments")
      (.insert (clj->js payment))
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

;; Booking expenses
(defn fetch-booking-expenses [booking-id callback]
  (-> (.from client "booking_expenses")
      (.select "*")
      (.eq "booking_id" booking-id)
      (.order "date" (clj->js {:ascending true}))
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-booking-expenses error" %))))

(defn add-booking-expense [expense callback]
  (-> (.from client "booking_expenses")
      (.insert (clj->js expense))
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

;; Overhead expenses
(defn fetch-overhead [month year callback]
  (-> (.from client "overhead_expenses")
      (.select "*")
      (.eq "month" month)
      (.eq "year" year)
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-overhead error" %))))

(defn add-overhead [expense callback]
  (-> (.from client "overhead_expenses")
      (.insert (clj->js expense))
      (.then #(callback {:ok true}))
      (.catch #(callback {:ok false}))))

;; P&L: fetch completed bookings for a month with their line items and expenses
(defn fetch-pnl [month year callback]
  (-> (.from client "bookings")
      (.select "id, customer_name, event_date, booking_line_items(amount), booking_expenses(amount)")
      (.eq "status" "completed")
      (.gte "event_date" (str year "-" (-> month str (.padStart 2 "0")) "-01"))
      (.lte "event_date" (str year "-" (-> month str (.padStart 2 "0")) "-31"))
      (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
      (.catch #(js/console.error "fetch-pnl error" %))))
```

- [ ] **Step 3: Verify build compiles**

```bash
cd ~/bk-hall && npx shadow-cljs compile app
```

Expected: 0 warnings.

- [ ] **Step 4: Commit**

```bash
git add src/bk/admin/state.cljs src/bk/supabase.cljs
git commit -m "feat: add admin state atom and Supabase auth/admin functions"
```

---

### Task 3: Supabase atomic booking creation function

**Files:**
- Create: `supabase/create_booking_fn.sql`

The `create_booking_with_items` Postgres function inserts booking + line items + first payment atomically.

- [ ] **Step 1: Create `supabase/create_booking_fn.sql`**

```sql
create or replace function create_booking_with_items(
  p_booking jsonb,
  p_line_items jsonb,
  p_payment jsonb
) returns uuid as $$
declare
  v_booking_id uuid;
begin
  insert into bookings (customer_name, phone, event_date, event_type, guest_count, addons, notes, status)
  values (
    p_booking->>'customer_name',
    p_booking->>'phone',
    (p_booking->>'event_date')::date,
    p_booking->>'event_type',
    (p_booking->>'guest_count')::int,
    array(select jsonb_array_elements_text(p_booking->'addons')),
    p_booking->>'notes',
    'confirmed'
  )
  returning id into v_booking_id;

  insert into booking_line_items (booking_id, label, amount)
  select v_booking_id, item->>'label', (item->>'amount')::numeric
  from jsonb_array_elements(p_line_items) as item;

  insert into booking_payments (booking_id, amount, date, note)
  values (
    v_booking_id,
    (p_payment->>'amount')::numeric,
    (p_payment->>'date')::date,
    p_payment->>'note'
  );

  return v_booking_id;
end;
$$ language plpgsql security definer;
```

- [ ] **Step 2: Run in Supabase SQL Editor**

Paste the contents of `supabase/create_booking_fn.sql` and run it.

- [ ] **Step 3: Commit**

```bash
git add supabase/create_booking_fn.sql
git commit -m "chore: add atomic booking creation Postgres function"
```

---

### Task 4: Router — detect /admin path and mount admin vs public

**Files:**
- Modify: `src/bk/core.cljs`

- [ ] **Step 1: Update `src/bk/core.cljs`**

```clojure
(ns bk.core
  (:require [reagent.dom :as rdom]
            [bk.public.page :as page]
            [bk.admin.shell :as admin]
            [bk.admin.state :as state]
            [bk.supabase :as db]))

(defn- admin-path? []
  (clojure.string/starts-with? (.-pathname js/location) "/admin"))

(defn- boot-admin []
  (db/on-auth-change
   (fn [session]
     (reset! state/session session)
     (if session
       (do
         (db/fetch-role #(reset! state/role %))
         (reset! state/screen :inquiries))
       (reset! state/screen :login))))
  (db/get-session
   (fn [session]
     (reset! state/session session)
     (if session
       (do
         (db/fetch-role #(reset! state/role %))
         (reset! state/screen :inquiries))
       (reset! state/screen :login)))))

(defn init []
  (if (admin-path?)
    (do
      (boot-admin)
      (rdom/render [admin/shell] (.getElementById js/document "app")))
    (rdom/render [page/root] (.getElementById js/document "app"))))
```

- [ ] **Step 2: Verify build compiles (admin.shell doesn't exist yet — expect a compile error)**

```bash
npx shadow-cljs compile app
```

Expected: error about missing `bk.admin.shell`. That's fine — we'll fix it in Task 5.

- [ ] **Step 3: Do NOT commit yet** — commit together with Task 5.

---

### Task 5: Login form

**Files:**
- Create: `src/bk/admin/login.cljs`
- Create: `src/bk/admin/shell.cljs` (stub, expanded in Task 6)

- [ ] **Step 1: Create `src/bk/admin/login.cljs`**

```clojure
(ns bk.admin.login
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(defn page []
  (let [email (r/atom "")
        password (r/atom "")
        error (r/atom nil)
        loading? (r/atom false)]
    (fn []
      [:div {:style {:min-height "100vh" :display "flex" :align-items "center"
                     :justify-content "center" :background "var(--cream)"}}
       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "48px 40px"
                      :box-shadow "var(--shadow)" :width "100%" :max-width "400px"}}
        [:h1 {:style {:font-size "1.5rem" :font-weight "800" :margin-bottom "8px"
                      :color "var(--dark)"}} "BK Function Hall"]
        [:p {:style {:color "var(--text-light)" :margin-bottom "32px"}} "Admin Panel"]
        [:div.form-group
         [:label.form-label "Email"]
         [:input.form-input {:type "email" :placeholder "admin@example.com"
                             :value @email
                             :on-change #(reset! email (.. % -target -value))}]]
        [:div.form-group
         [:label.form-label "Password"]
         [:input.form-input {:type "password"
                             :value @password
                             :on-change #(reset! password (.. % -target -value))}]]
        (when @error
          [:p {:style {:color "#dc2626" :font-size "14px" :margin-bottom "16px"}} @error])
        [:button {:class "btn-primary"
                  :style {:width "100%"}
                  :disabled @loading?
                  :on-click (fn []
                              (reset! error nil)
                              (reset! loading? true)
                              (db/sign-in @email @password
                                (fn [{:keys [ok error]}]
                                  (reset! loading? false)
                                  (if ok
                                    (do
                                      (db/fetch-role #(reset! state/role %))
                                      (reset! state/screen :inquiries))
                                    (reset! error error)))))}
         (if @loading? "Signing in…" "Sign In")]]])))
```

- [ ] **Step 2: Create `src/bk/admin/shell.cljs` (stub)**

```clojure
(ns bk.admin.shell
  (:require [bk.admin.state :as state]
            [bk.admin.login :as login]))

(defn shell []
  (if (nil? @state/session)
    [login/page]
    [:div "Admin — logged in"]))
```

- [ ] **Step 3: Verify build compiles**

```bash
npx shadow-cljs compile app
```

Expected: 0 warnings.

- [ ] **Step 4: Commit**

```bash
git add src/bk/core.cljs src/bk/admin/state.cljs src/bk/admin/login.cljs src/bk/admin/shell.cljs
git commit -m "feat: add admin router, login form, and shell stub"
```

- [ ] **Step 5: Test login in browser**

```bash
npx shadow-cljs watch app
```

Navigate to http://localhost:3000/admin — should see the login form. Sign in with the owner account you create in Supabase → Authentication → Users.

---

### Task 6: Admin shell with navigation

**Files:**
- Modify: `src/bk/admin/shell.cljs`

- [ ] **Step 1: Update `src/bk/admin/shell.cljs`**

```clojure
(ns bk.admin.shell
  (:require [bk.admin.state :as state]
            [bk.admin.login :as login]
            [bk.admin.inquiries :as inquiries]
            [bk.admin.bookings :as bookings]
            [bk.admin.booking-detail :as booking-detail]
            [bk.admin.booking-form :as booking-form]
            [bk.admin.overhead :as overhead]
            [bk.admin.pnl :as pnl]
            [bk.supabase :as db]))

(def nav-items
  [{:screen :inquiries  :label "Inquiries"}
   {:screen :bookings   :label "Bookings"}
   {:screen :overhead   :label "Overhead"}
   {:screen :pnl        :label "P&L" :owner-only? true}])

(defn nav []
  [:nav {:style {:background "var(--dark)" :color "#fff" :padding "0 24px"
                 :display "flex" :align-items "center" :gap "8px" :height "56px"}}
   [:span {:style {:font-weight "700" :margin-right "24px" :color "var(--gold-light)"}}
    "BK Admin"]
   (for [{:keys [screen label owner-only?]} nav-items]
     (when (or (not owner-only?) (= @state/role "owner"))
       ^{:key screen}
       [:button {:on-click #(reset! state/screen screen)
                 :style {:background (if (= @state/screen screen) "rgba(255,255,255,0.15)" "none")
                         :border "none" :color "#fff" :padding "8px 16px" :border-radius "6px"
                         :cursor "pointer" :font-size "14px" :font-weight "600"}}
        label]))
   [:div {:style {:margin-left "auto"}}
    [:button {:on-click #(db/sign-out (fn [_] (reset! state/session nil) (reset! state/role nil) (reset! state/screen :login)))
              :style {:background "none" :border "1px solid rgba(255,255,255,0.3)" :color "rgba(255,255,255,0.7)"
                      :padding "6px 14px" :border-radius "6px" :cursor "pointer" :font-size "13px"}}
     "Sign Out"]]])

(defn current-screen []
  (case @state/screen
    :inquiries  [inquiries/page]
    :bookings   [bookings/page]
    :booking-detail [booking-detail/page]
    :new-booking [booking-form/page]
    :overhead   [overhead/page]
    :pnl        (when (= @state/role "owner") [pnl/page])
    [:div "Unknown screen"]))

(defn shell []
  (if (nil? @state/session)
    [login/page]
    [:div {:style {:min-height "100vh" :background "var(--cream)"}}
     [nav]
     [:div {:style {:padding "32px 24px" :max-width "1100px" :margin "0 auto"}}
      [current-screen]]]))
```

- [ ] **Step 2: Verify build compiles (missing namespaces expected)**

```bash
npx shadow-cljs compile app
```

Expected: errors about missing `bk.admin.inquiries`, `bk.admin.bookings`, etc. — will be fixed in subsequent tasks.

- [ ] **Step 3: Do NOT commit yet** — commit after Task 7 when it compiles.

---

### Task 7: Inquiries screen

**Files:**
- Create: `src/bk/admin/inquiries.cljs`

- [ ] **Step 1: Create `src/bk/admin/inquiries.cljs`**

```clojure
(ns bk.admin.inquiries
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(def status-options ["new" "called back" "converted" "not interested"])

(defn status-badge [status]
  (let [colors {"new" "#3b82f6" "called back" "#f59e0b"
                "converted" "#16a34a" "not interested" "#9ca3af"}]
    [:span {:style {:background (get colors status "#9ca3af")
                    :color "#fff" :padding "2px 10px" :border-radius "50px"
                    :font-size "12px" :font-weight "600"}}
     status]))

(defn inquiry-row [inq on-status-change]
  (let [{:keys [id name phone event_date event_type guest_count addons message status]} inq]
    [:tr {:style {:border-bottom "1px solid var(--cream-dark)"}}
     [:td {:style {:padding "14px 12px"}}
      [:div {:style {:font-weight "600"}} name]
      [:div {:style {:color "var(--text-light)" :font-size "13px"}} phone]]
     [:td {:style {:padding "14px 12px"}}
      [:div event_date]
      [:div {:style {:color "var(--text-light)" :font-size "13px"}} event_type]]
     [:td {:style {:padding "14px 12px" :color "var(--text-light)"}} (str guest_count " guests")]
     [:td {:style {:padding "14px 12px" :color "var(--text-light)" :font-size "13px"}}
      (clojure.string/join ", " addons)]
     [:td {:style {:padding "14px 12px" :color "var(--text-light)" :font-size "13px" :max-width "200px"}} message]
     [:td {:style {:padding "14px 12px"}}
      [:select {:value status
                :on-change #(on-status-change id (.. % -target -value))
                :style {:border "1px solid var(--cream-dark)" :border-radius "6px"
                        :padding "4px 8px" :font-size "13px" :cursor "pointer"}}
       (for [s status-options]
         ^{:key s} [:option {:value s} s])]]
     (when (= @state/role "owner")
       [:td {:style {:padding "14px 12px"}}
        (when (= status "converted")
          [:button {:on-click #(do (reset! state/screen-params {:inquiry inq})
                                   (reset! state/screen :new-booking))
                    :style {:background "var(--gold)" :color "#fff" :border "none"
                            :padding "6px 12px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px" :font-weight "600"}}
           "Create Booking"])])]))

(defn page []
  (let [inquiries (r/atom [])
        loading? (r/atom true)]
    (db/fetch-inquiries (fn [data]
                          (reset! inquiries data)
                          (reset! loading? false)))
    (fn []
      [:div
       [:h2 {:style {:font-size "1.4rem" :font-weight "700" :margin-bottom "24px"}} "Inquiries"]
       (if @loading?
         [:p "Loading…"]
         [:div {:style {:background "#fff" :border-radius "var(--radius)" :overflow "hidden"
                        :box-shadow "var(--shadow)"}}
          [:table {:style {:width "100%" :border-collapse "collapse"}}
           [:thead {:style {:background "var(--cream)"}}
            [:tr
             (for [h ["Customer" "Date / Type" "Guests" "Add-ons" "Message" "Status"
                      (when (= @state/role "owner") "Action")]]
               (when h
                 ^{:key h}
                 [:th {:style {:padding "12px" :text-align "left" :font-size "13px"
                               :font-weight "700" :color "var(--text-light)"}} h]))]]
           [:tbody
            (for [inq @inquiries]
              ^{:key (:id inq)}
              [inquiry-row inq
               (fn [id status]
                 (db/update-inquiry-status id status
                   (fn [{:keys [ok]}]
                     (when ok
                       (swap! inquiries #(mapv (fn [i] (if (= (:id i) id) (assoc i :status status) i)) %))))))])]]])])))
```

- [ ] **Step 2: Verify compiles (still missing other namespaces)**

```bash
npx shadow-cljs compile app
```

- [ ] **Step 3: Do NOT commit yet.**

---

### Task 8: Bookings list screen

**Files:**
- Create: `src/bk/admin/bookings.cljs`

- [ ] **Step 1: Create `src/bk/admin/bookings.cljs`**

```clojure
(ns bk.admin.bookings
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(def status-colors
  {"confirmed" "#3b82f6" "completed" "#16a34a" "cancelled" "#dc2626"})

(defn page []
  (let [bookings (r/atom [])
        loading? (r/atom true)]
    (db/fetch-bookings (fn [data]
                         (reset! bookings data)
                         (reset! loading? false)))
    (fn []
      [:div
       [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom "24px"}}
        [:h2 {:style {:font-size "1.4rem" :font-weight "700"}} "Bookings"]
        (when (= @state/role "owner")
          [:button {:on-click #(do (reset! state/screen-params {})
                                   (reset! state/screen :new-booking))
                    :class "btn-primary"
                    :style {:padding "10px 20px" :font-size "14px"}}
           "+ New Booking"])]
       (if @loading?
         [:p "Loading…"]
         [:div {:style {:background "#fff" :border-radius "var(--radius)" :overflow "hidden"
                        :box-shadow "var(--shadow)"}}
          [:table {:style {:width "100%" :border-collapse "collapse"}}
           [:thead {:style {:background "var(--cream)"}}
            [:tr
             (for [h ["Date" "Customer" "Type" "Guests" "Status"]]
               ^{:key h}
               [:th {:style {:padding "12px" :text-align "left" :font-size "13px"
                             :font-weight "700" :color "var(--text-light)"}} h])]]
           [:tbody
            (for [b @bookings]
              ^{:key (:id b)}
              [:tr {:style {:border-bottom "1px solid var(--cream-dark)" :cursor "pointer"}
                    :on-click #(do (reset! state/screen-params {:booking-id (:id b)})
                                   (reset! state/screen :booking-detail))}
               [:td {:style {:padding "14px 12px"}} (:event_date b)]
               [:td {:style {:padding "14px 12px"}}
                [:div {:style {:font-weight "600"}} (:customer_name b)]
                [:div {:style {:color "var(--text-light)" :font-size "13px"}} (:phone b)]]
               [:td {:style {:padding "14px 12px" :color "var(--text-light)"}} (:event_type b)]
               [:td {:style {:padding "14px 12px" :color "var(--text-light)"}} (str (:guest_count b) " guests")]
               [:td {:style {:padding "14px 12px"}}
                [:span {:style {:background (get status-colors (:status b) "#9ca3af")
                                :color "#fff" :padding "2px 10px" :border-radius "50px"
                                :font-size "12px" :font-weight "600"}}
                 (:status b)]]])]]])])))
```

- [ ] **Step 2: Still missing booking-detail, booking-form, overhead, pnl — keep going.**

---

### Task 9: Booking creation form (owner only)

**Files:**
- Create: `src/bk/admin/booking_form.cljs`

- [ ] **Step 1: Create `src/bk/admin/booking_form.cljs`**

```clojure
(ns bk.admin.booking-form
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(def addon-options ["Decoration" "Catering" "Stage Setup" "Photography" "DJ / Music"])

(defn line-item-row [item on-change on-remove]
  [:div {:style {:display "flex" :gap "12px" :margin-bottom "8px" :align-items "center"}}
   [:input.form-input {:placeholder "Label (e.g. Venue hire)"
                       :style {:flex "2"}
                       :value (:label item)
                       :on-change #(on-change :label (.. % -target -value))}]
   [:input.form-input {:type "number" :placeholder "Amount"
                       :style {:flex "1"}
                       :value (:amount item)
                       :on-change #(on-change :amount (.. % -target -value))}]
   [:button {:on-click on-remove
             :style {:background "none" :border "none" :color "#dc2626"
                     :font-size "20px" :cursor "pointer" :line-height "1"}} "×"]])

(defn page []
  (let [prefill (get @state/screen-params :inquiry)
        form (r/atom {:customer_name (or (:name prefill) "")
                      :phone (or (:phone prefill) "")
                      :event_date (or (:event_date prefill) "")
                      :event_type (or (:event_type prefill) "wedding")
                      :guest_count (or (str (:guest_count prefill)) "")
                      :addons (or (:addons prefill) [])
                      :notes ""})
        line-items (r/atom [{:label "Venue hire" :amount ""}])
        payment (r/atom {:amount "" :date "" :note "Advance"})
        error (r/atom nil)
        saving? (r/atom false)]
    (fn []
      [:div {:style {:max-width "760px"}}
       [:div {:style {:display "flex" :align-items "center" :gap "16px" :margin-bottom "24px"}}
        [:button {:on-click #(reset! state/screen :bookings)
                  :style {:background "none" :border "none" :color "var(--gold)"
                          :cursor "pointer" :font-size "14px"}} "← Back"]
        [:h2 {:style {:font-size "1.4rem" :font-weight "700"}} "New Booking"]]

       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "32px"
                      :box-shadow "var(--shadow)"}}
        [:h3 {:style {:font-weight "700" :margin-bottom "20px" :color "var(--dark)"}} "Customer Details"]
        [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
         [:div.form-group
          [:label.form-label "Customer Name *"]
          [:input.form-input {:required true :value (:customer_name @form)
                              :on-change #(swap! form assoc :customer_name (.. % -target -value))}]]
         [:div.form-group
          [:label.form-label "Phone *"]
          [:input.form-input {:required true :value (:phone @form)
                              :on-change #(swap! form assoc :phone (.. % -target -value))}]]
         [:div.form-group
          [:label.form-label "Event Date *"]
          [:input.form-input {:type "date" :required true :value (:event_date @form)
                              :on-change #(swap! form assoc :event_date (.. % -target -value))}]]
         [:div.form-group
          [:label.form-label "Event Type *"]
          [:select.form-input {:value (:event_type @form)
                               :on-change #(swap! form assoc :event_type (.. % -target -value))}
           [:option {:value "wedding"} "Wedding"]
           [:option {:value "reception"} "Reception"]
           [:option {:value "birthday"} "Birthday"]
           [:option {:value "other"} "Other"]]]
         [:div.form-group
          [:label.form-label "Guest Count"]
          [:input.form-input {:type "number" :value (:guest_count @form)
                              :on-change #(swap! form assoc :guest_count (.. % -target -value))}]]]
        [:div.form-group
         [:label.form-label "Add-ons"]
         [:div {:style {:display "flex" :flex-wrap "wrap" :gap "10px" :margin-top "8px"}}
          (for [addon addon-options]
            ^{:key addon}
            (let [selected? (contains? (set (:addons @form)) addon)]
              [:label {:style {:display "flex" :align-items "center" :gap "6px" :cursor "pointer" :font-size "14px"}}
               [:input {:type "checkbox" :checked selected?
                        :on-change #(swap! form update :addons
                                           (fn [a] (if selected? (filterv #(not= % addon) a) (conj a addon))))}]
               addon]))]]
        [:div.form-group
         [:label.form-label "Notes"]
         [:textarea.form-input {:rows 3 :value (:notes @form)
                                :on-change #(swap! form assoc :notes (.. % -target -value))}]]

        [:hr {:style {:border "none" :border-top "1px solid var(--cream-dark)" :margin "24px 0"}}]
        [:h3 {:style {:font-weight "700" :margin-bottom "16px" :color "var(--dark)"}} "Line Items (What you're charging)"]
        (map-indexed
         (fn [i item]
           ^{:key i}
           [line-item-row item
            (fn [field val] (swap! line-items assoc-in [i field] val))
            (fn [] (swap! line-items #(into [] (concat (subvec % 0 i) (subvec % (inc i))))))])
         @line-items)
        [:button {:on-click #(swap! line-items conj {:label "" :amount ""})
                  :style {:background "none" :border "1.5px dashed var(--cream-dark)"
                          :border-radius "8px" :padding "8px 16px" :cursor "pointer"
                          :color "var(--text-light)" :font-size "14px" :width "100%"
                          :margin-bottom "24px"}}
         "+ Add Line Item"]

        [:hr {:style {:border "none" :border-top "1px solid var(--cream-dark)" :margin "24px 0"}}]
        [:h3 {:style {:font-weight "700" :margin-bottom "16px" :color "var(--dark)"}} "Advance Payment"]
        [:div {:style {:display "grid" :grid-template-columns "1fr 1fr 1fr" :gap "16px"}}
         [:div.form-group
          [:label.form-label "Amount *"]
          [:input.form-input {:type "number" :value (:amount @payment)
                              :on-change #(swap! payment assoc :amount (.. % -target -value))}]]
         [:div.form-group
          [:label.form-label "Date *"]
          [:input.form-input {:type "date" :value (:date @payment)
                              :on-change #(swap! payment assoc :date (.. % -target -value))}]]
         [:div.form-group
          [:label.form-label "Note"]
          [:input.form-input {:value (:note @payment)
                              :on-change #(swap! payment assoc :note (.. % -target -value))}]]]

        (when @error
          [:p {:style {:color "#dc2626" :margin-bottom "16px"}} @error])

        [:button {:class "btn-primary"
                  :disabled @saving?
                  :on-click (fn []
                              (reset! error nil)
                              (reset! saving? true)
                              (let [booking (-> @form
                                               (update :guest_count #(when (seq %) (js/parseInt %))))
                                    items (mapv #(update % :amount js/parseFloat) @line-items)
                                    pmt (update @payment :amount js/parseFloat)]
                                (db/create-booking booking items pmt
                                  (fn [{:keys [ok error]}]
                                    (reset! saving? false)
                                    (if ok
                                      (reset! state/screen :bookings)
                                      (reset! error (or error "Something went wrong")))))))}
         (if @saving? "Saving…" "Confirm Booking")]])))
```

---

### Task 10: Booking detail — operational + financial sections

**Files:**
- Create: `src/bk/admin/booking_detail.cljs`

- [ ] **Step 1: Create `src/bk/admin/booking_detail.cljs`**

```clojure
(ns bk.admin.booking-detail
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(defn fmt-currency [n]
  (str "₹" (.toLocaleString n "en-IN")))

(defn whatsapp-message [booking line-items payments]
  (let [total (reduce + (map :amount line-items))
        paid (reduce + (map :amount payments))
        due (- total paid)
        lines (clojure.string/join "\n" (map #(str "  " (:label %) ": " (fmt-currency (:amount %))) line-items))]
    (js/encodeURIComponent
     (str "Dear " (:customer_name booking) ",\n\n"
          "Confirming your booking at BK Function Hall:\n"
          "Date: " (:event_date booking) "\n"
          "Event: " (:event_type booking) "\n"
          "Guests: " (:guest_count booking) "\n\n"
          "Charges:\n" lines "\n\n"
          "Total: " (fmt-currency total) "\n"
          "Advance Paid: " (fmt-currency paid) "\n"
          "Balance Due: " (fmt-currency due) "\n\n"
          "Thank you for choosing BK Function Hall!"))))

(defn add-line-item-form [booking-id on-added]
  (let [form (r/atom {:label "" :amount ""})
        saving? (r/atom false)]
    (fn []
      [:div {:style {:display "flex" :gap "12px" :margin-top "12px" :align-items "flex-end"}}
       [:div {:style {:flex "2"}}
        [:label.form-label "Label"]
        [:input.form-input {:value (:label @form)
                            :on-change #(swap! form assoc :label (.. % -target -value))}]]
       [:div {:style {:flex "1"}}
        [:label.form-label "Amount"]
        [:input.form-input {:type "number" :value (:amount @form)
                            :on-change #(swap! form assoc :amount (.. % -target -value))}]]
       [:button {:class "btn-primary" :style {:padding "10px 20px" :font-size "14px"}
                 :disabled @saving?
                 :on-click (fn []
                             (reset! saving? true)
                             (db/add-line-item {:booking_id booking-id
                                               :label (:label @form)
                                               :amount (js/parseFloat (:amount @form))}
                               (fn [{:keys [ok]}]
                                 (reset! saving? false)
                                 (when ok (on-added) (reset! form {:label "" :amount ""})))))}
        "Add"]])))

(defn add-payment-form [booking-id on-added]
  (let [form (r/atom {:amount "" :date "" :note ""})
        saving? (r/atom false)]
    (fn []
      [:div {:style {:display "flex" :gap "12px" :margin-top "12px" :align-items "flex-end"}}
       [:div {:style {:flex "1"}}
        [:label.form-label "Amount"]
        [:input.form-input {:type "number" :value (:amount @form)
                            :on-change #(swap! form assoc :amount (.. % -target -value))}]]
       [:div {:style {:flex "1"}}
        [:label.form-label "Date"]
        [:input.form-input {:type "date" :value (:date @form)
                            :on-change #(swap! form assoc :date (.. % -target -value))}]]
       [:div {:style {:flex "1"}}
        [:label.form-label "Note"]
        [:input.form-input {:value (:note @form)
                            :on-change #(swap! form assoc :note (.. % -target -value))}]]
       [:button {:class "btn-primary" :style {:padding "10px 20px" :font-size "14px"}
                 :disabled @saving?
                 :on-click (fn []
                             (reset! saving? true)
                             (db/add-payment {:booking_id booking-id
                                             :amount (js/parseFloat (:amount @form))
                                             :date (:date @form)
                                             :note (:note @form)}
                               (fn [{:keys [ok]}]
                                 (reset! saving? false)
                                 (when ok (on-added) (reset! form {:amount "" :date "" :note ""})))))}
        "Add"]])))

(defn add-expense-form [booking-id on-added]
  (let [form (r/atom {:label "" :amount "" :date "" :category "labour"})
        saving? (r/atom false)]
    (fn []
      [:div {:style {:display "grid" :grid-template-columns "2fr 1fr 1fr 1fr auto" :gap "12px"
                     :margin-top "12px" :align-items "flex-end"}}
       [:div
        [:label.form-label "Label"]
        [:input.form-input {:value (:label @form)
                            :on-change #(swap! form assoc :label (.. % -target -value))}]]
       [:div
        [:label.form-label "Amount"]
        [:input.form-input {:type "number" :value (:amount @form)
                            :on-change #(swap! form assoc :amount (.. % -target -value))}]]
       [:div
        [:label.form-label "Date"]
        [:input.form-input {:type "date" :value (:date @form)
                            :on-change #(swap! form assoc :date (.. % -target -value))}]]
       [:div
        [:label.form-label "Category"]
        [:select.form-input {:value (:category @form)
                             :on-change #(swap! form assoc :category (.. % -target -value))}
         (for [c ["electricity" "labour" "catering" "decoration" "other"]]
           ^{:key c} [:option {:value c} c])]]
       [:button {:class "btn-primary" :style {:padding "10px 20px" :font-size "14px"}
                 :disabled @saving?
                 :on-click (fn []
                             (reset! saving? true)
                             (db/add-booking-expense {:booking_id booking-id
                                                     :label (:label @form)
                                                     :amount (js/parseFloat (:amount @form))
                                                     :date (:date @form)
                                                     :category (:category @form)}
                               (fn [{:keys [ok]}]
                                 (reset! saving? false)
                                 (when ok (on-added) (reset! form {:label "" :amount "" :date "" :category "labour"})))))}
        "Add"]])))

(defn page []
  (let [booking-id (get @state/screen-params :booking-id)
        booking (r/atom nil)
        line-items (r/atom [])
        payments (r/atom [])
        expenses (r/atom [])
        owner? (= @state/role "owner")
        load-financials (fn []
                          (db/fetch-line-items booking-id #(reset! line-items %))
                          (db/fetch-payments booking-id #(reset! payments %))
                          (db/fetch-booking-expenses booking-id #(reset! expenses %)))]
    (db/fetch-booking booking-id #(reset! booking %))
    (when owner? (load-financials))
    (fn []
      (if (nil? @booking)
        [:p "Loading…"]
        (let [{:keys [customer_name phone event_date event_type guest_count addons notes status]} @booking
              total (reduce + (map :amount @line-items))
              paid (reduce + (map :amount @payments))
              due (- total paid)]
          [:div {:style {:max-width "860px"}}
           [:div {:style {:display "flex" :align-items "center" :gap "16px" :margin-bottom "24px"}}
            [:button {:on-click #(reset! state/screen :bookings)
                      :style {:background "none" :border "none" :color "var(--gold)"
                              :cursor "pointer" :font-size "14px"}} "← Bookings"]
            [:h2 {:style {:font-size "1.4rem" :font-weight "700"}} customer_name]
            [:select {:value status
                      :on-change #(db/update-booking-status booking-id (.. % -target -value)
                                    (fn [{:keys [ok]}]
                                      (when ok (swap! booking assoc :status (.. % -target -value)))))
                      :style {:border "1px solid var(--cream-dark)" :border-radius "6px"
                              :padding "4px 8px" :font-size "13px"}}
             (for [s ["confirmed" "completed" "cancelled"]]
               ^{:key s} [:option {:value s} s])]]

           ;; Operational section
           [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                          :box-shadow "var(--shadow)" :margin-bottom "24px"}}
            [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "Event Details"]
            [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
             [:div [:span {:style {:color "var(--text-light)" :font-size "13px"}} "Phone"] [:div phone]]
             [:div [:span {:style {:color "var(--text-light)" :font-size "13px"}} "Date"] [:div event_date]]
             [:div [:span {:style {:color "var(--text-light)" :font-size "13px"}} "Event Type"] [:div event_type]]
             [:div [:span {:style {:color "var(--text-light)" :font-size "13px"}} "Guests"] [:div (str guest_count)]]
             [:div [:span {:style {:color "var(--text-light)" :font-size "13px"}} "Add-ons"]
              [:div (clojure.string/join ", " addons)]]
             [:div [:span {:style {:color "var(--text-light)" :font-size "13px"}} "Notes"] [:div (or notes "—")]]]]

           ;; Financial section — owner only
           (when owner?
             [:<>
              ;; Line items
              [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                             :box-shadow "var(--shadow)" :margin-bottom "24px"}}
               [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "Charges (Line Items)"]
               (for [item @line-items]
                 ^{:key (:id item)}
                 [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                                :border-bottom "1px solid var(--cream-dark)"}}
                  [:span (:label item)]
                  [:span {:style {:font-weight "600"}} (fmt-currency (:amount item))]])
               [:div {:style {:display "flex" :justify-content "space-between" :padding "12px 0"
                              :font-weight "700" :font-size "1.05rem"}}
                [:span "Total"] [:span (fmt-currency total)]]
               [add-line-item-form booking-id load-financials]]

              ;; Payments
              [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                             :box-shadow "var(--shadow)" :margin-bottom "24px"}}
               [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "Payments"]
               (for [pmt @payments]
                 ^{:key (:id pmt)}
                 [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                                :border-bottom "1px solid var(--cream-dark)"}}
                  [:span (str (:date pmt) (when (:note pmt) (str " — " (:note pmt))))]
                  [:span {:style {:font-weight "600" :color "#16a34a"}} (fmt-currency (:amount pmt))]])
               [:div {:style {:display "flex" :justify-content "space-between" :padding "12px 0"
                              :border-top "2px solid var(--cream-dark)"}}
                [:span "Paid"] [:span {:style {:color "#16a34a" :font-weight "700"}} (fmt-currency paid)]]
               [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                              :font-weight "700" :font-size "1.05rem"
                              :color (if (pos? due) "#dc2626" "#16a34a")}}
                [:span "Balance Due"] [:span (fmt-currency due)]]
               [add-payment-form booking-id load-financials]

               ;; WhatsApp button
               [:div {:style {:margin-top "20px" :text-align "right"}}
                [:a {:href (str "https://wa.me/" (clojure.string/replace phone #"[^0-9]" "")
                                "?text=" (whatsapp-message @booking @line-items @payments))
                     :target "_blank"
                     :class "btn-primary"
                     :style {:display "inline-flex" :align-items "center" :gap "8px"}}
                 "📱 Send WhatsApp Confirmation"]]]

              ;; Per-event expenses
              [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                             :box-shadow "var(--shadow)"}}
               [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "Event Expenses (What you spent)"]
               (for [exp @expenses]
                 ^{:key (:id exp)}
                 [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                                :border-bottom "1px solid var(--cream-dark)"}}
                  [:span (str (:label exp) " — " (:date exp)
                              " [" (:category exp) "]")]
                  [:span {:style {:font-weight "600" :color "#dc2626"}} (fmt-currency (:amount exp))]])
               [add-expense-form booking-id load-financials]]])))]))))
```

---

### Task 11: Overhead expenses screen

**Files:**
- Create: `src/bk/admin/overhead.cljs`

- [ ] **Step 1: Create `src/bk/admin/overhead.cljs`**

```clojure
(ns bk.admin.overhead
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(defn page []
  (let [now (js/Date.)
        month (r/atom (inc (.getMonth now)))
        year (r/atom (.getFullYear now))
        expenses (r/atom [])
        form (r/atom {:label "" :amount "" :category "electricity"})
        saving? (r/atom false)
        load #(db/fetch-overhead @month @year (fn [data] (reset! expenses data)))]
    (load)
    (fn []
      [:div {:style {:max-width "760px"}}
       [:h2 {:style {:font-size "1.4rem" :font-weight "700" :margin-bottom "24px"}} "Overhead Expenses"]

       ;; Month picker
       [:div {:style {:display "flex" :gap "12px" :align-items "center" :margin-bottom "24px"}}
        [:select.form-input {:style {:width "160px"} :value @month
                             :on-change #(do (reset! month (js/parseInt (.. % -target -value))) (load))}
         (map-indexed (fn [i m] ^{:key i} [:option {:value (inc i)} m])
                      ["January" "February" "March" "April" "May" "June"
                       "July" "August" "September" "October" "November" "December"])]
        [:input.form-input {:type "number" :style {:width "100px"} :value @year
                            :on-change #(do (reset! year (js/parseInt (.. % -target -value))) (load))}]]

       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                      :box-shadow "var(--shadow)" :margin-bottom "24px"}}
        (if (empty? @expenses)
          [:p {:style {:color "var(--text-light)"}} "No expenses for this month."]
          (for [exp @expenses]
            ^{:key (:id exp)}
            [:div {:style {:display "flex" :justify-content "space-between" :padding "10px 0"
                           :border-bottom "1px solid var(--cream-dark)"}}
             [:span (str (:label exp) " [" (:category exp) "]")]
             [:span {:style {:font-weight "600" :color "#dc2626"}}
              (str "₹" (.toLocaleString (:amount exp) "en-IN"))]]))
        (when (seq @expenses)
          [:div {:style {:display "flex" :justify-content "space-between" :padding "12px 0"
                         :font-weight "700"}}
           [:span "Total"]
           [:span (str "₹" (.toLocaleString (reduce + (map :amount @expenses)) "en-IN"))]])]

       ;; Add form
       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                      :box-shadow "var(--shadow)"}}
        [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "Add Expense"]
        [:div {:style {:display "grid" :grid-template-columns "2fr 1fr 1fr auto" :gap "12px"
                       :align-items "flex-end"}}
         [:div
          [:label.form-label "Label"]
          [:input.form-input {:placeholder "e.g. Electricity bill — April"
                              :value (:label @form)
                              :on-change #(swap! form assoc :label (.. % -target -value))}]]
         [:div
          [:label.form-label "Amount"]
          [:input.form-input {:type "number" :value (:amount @form)
                              :on-change #(swap! form assoc :amount (.. % -target -value))}]]
         [:div
          [:label.form-label "Category"]
          [:select.form-input {:value (:category @form)
                               :on-change #(swap! form assoc :category (.. % -target -value))}
           (for [c ["electricity" "maintenance" "cleaning" "labour" "other"]]
             ^{:key c} [:option {:value c} c])]]
         [:button {:class "btn-primary" :style {:padding "10px 20px"}
                   :disabled @saving?
                   :on-click (fn []
                               (reset! saving? true)
                               (db/add-overhead {:label (:label @form)
                                                :amount (js/parseFloat (:amount @form))
                                                :month @month
                                                :year @year
                                                :category (:category @form)}
                                 (fn [{:keys [ok]}]
                                   (reset! saving? false)
                                   (when ok
                                     (load)
                                     (reset! form {:label "" :amount "" :category "electricity"})))))}
          "Add"]]]])))
```

---

### Task 12: Monthly P&L screen

**Files:**
- Create: `src/bk/admin/pnl.cljs`

- [ ] **Step 1: Create `src/bk/admin/pnl.cljs`**

```clojure
(ns bk.admin.pnl
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(def month-names ["January" "February" "March" "April" "May" "June"
                  "July" "August" "September" "October" "November" "December"])

(defn page []
  (let [now (js/Date.)
        month (r/atom (inc (.getMonth now)))
        year (r/atom (.getFullYear now))
        bookings (r/atom [])
        overhead (r/atom [])
        loading? (r/atom false)
        load (fn []
               (reset! loading? true)
               (db/fetch-pnl @month @year
                 (fn [data]
                   (reset! bookings data)
                   (db/fetch-overhead @month @year
                     (fn [oh]
                       (reset! overhead oh)
                       (reset! loading? false))))))]
    (load)
    (fn []
      (let [income (->> @bookings
                        (mapcat :booking_line_items)
                        (reduce #(+ % (:amount %2)) 0))
            event-expenses (->> @bookings
                                (mapcat :booking_expenses)
                                (reduce #(+ % (:amount %2)) 0))
            overhead-total (reduce #(+ % (:amount %2)) 0 @overhead)
            net (- income event-expenses overhead-total)]
        [:div {:style {:max-width "760px"}}
         [:h2 {:style {:font-size "1.4rem" :font-weight "700" :margin-bottom "24px"}} "Monthly P&L"]

         [:div {:style {:display "flex" :gap "12px" :align-items "center" :margin-bottom "24px"}}
          [:select.form-input {:style {:width "160px"} :value @month
                               :on-change #(do (reset! month (js/parseInt (.. % -target -value))) (load))}
           (map-indexed (fn [i m] ^{:key i} [:option {:value (inc i)} m]) month-names)]
          [:input.form-input {:type "number" :style {:width "100px"} :value @year
                              :on-change #(do (reset! year (js/parseInt (.. % -target -value))) (load))}]]

         (if @loading?
           [:p "Loading…"]
           [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "32px"
                          :box-shadow "var(--shadow)"}}
            [:h3 {:style {:font-weight "700" :margin-bottom "16px" :color "var(--text-light)"
                          :font-size "0.9rem" :text-transform "uppercase" :letter-spacing "1px"}}
             (str (nth month-names (dec @month)) " " @year)]

            (for [[label amount color]
                  [["Income (completed bookings)" income "#16a34a"]
                   ["Event Expenses" (- event-expenses) "#dc2626"]
                   ["Overhead Expenses" (- overhead-total) "#dc2626"]]]
              ^{:key label}
              [:div {:style {:display "flex" :justify-content "space-between" :padding "14px 0"
                             :border-bottom "1px solid var(--cream-dark)"}}
               [:span {:style {:font-size "1rem"}} label]
               [:span {:style {:font-weight "600" :color color :font-size "1rem"}}
                (str "₹" (.toLocaleString (js/Math.abs amount) "en-IN"))]])

            [:div {:style {:display "flex" :justify-content "space-between" :padding "16px 0"
                           :font-size "1.15rem" :font-weight "800"
                           :color (if (neg? net) "#dc2626" "#16a34a")}}
             [:span "Net Profit"]
             [:span (str (if (neg? net) "−" "") "₹" (.toLocaleString (js/Math.abs net) "en-IN"))]]

            (when (seq @bookings)
              [:div {:style {:margin-top "24px"}}
               [:h4 {:style {:font-weight "700" :margin-bottom "12px" :font-size "0.9rem"
                             :color "var(--text-light)" :text-transform "uppercase"}} "Events This Month"]
               (for [b @bookings]
                 ^{:key (:id b)}
                 [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                                :border-bottom "1px solid var(--cream-dark)" :font-size "14px"}}
                  [:span (str (:event_date b) " — " (:customer_name b))]
                  [:span {:style {:color "var(--text-light)"}}
                   (str "₹" (.toLocaleString (reduce #(+ % (:amount %2)) 0 (:booking_line_items b)) "en-IN"))]])])])]))))
```

---

### Task 13: Wire everything up — compile and commit

**Files:**
- Modify: `src/bk/admin/shell.cljs` (already written in Task 6, just needs the stubs filled)

- [ ] **Step 1: Verify all files exist**

```bash
ls src/bk/admin/
```

Expected: `state.cljs login.cljs shell.cljs inquiries.cljs bookings.cljs booking_form.cljs booking_detail.cljs overhead.cljs pnl.cljs`

- [ ] **Step 2: Compile**

```bash
npx shadow-cljs compile app
```

Expected: `[:app] Build completed. (N files, N compiled, 0 warnings)`

Fix any warnings before committing.

- [ ] **Step 3: Commit all admin files**

```bash
git add src/bk/core.cljs src/bk/supabase.cljs src/bk/admin/
git commit -m "feat: add admin panel — login, inquiries, bookings, financials, P&L"
```

- [ ] **Step 4: Test in browser**

```bash
npx shadow-cljs watch app
```

- Navigate to http://localhost:3000/admin — login form appears
- Sign in with owner credentials
- Inquiries screen loads (empty initially)
- Bookings screen loads, "+ New Booking" button visible
- Create a test booking — verify it appears in bookings list and in Supabase table editor
- Click booking — detail screen loads, line items and payments visible
- WhatsApp button generates a pre-filled `wa.me` link
- Overhead expenses — add one, verify it saves
- P&L — shows 0 until a booking is marked "completed"

- [ ] **Step 5: Push**

```bash
git push origin main
```

---

## Self-Review

**Spec coverage:**
- ✅ Auth: email+password login, session persistence, sign out
- ✅ Roles: owner vs staff enforced via `current_user_role()` in RLS + `@state/role` in UI
- ✅ Inquiries: list, status update (both roles), convert to booking (owner only)
- ✅ Bookings: list, create (owner only), status update
- ✅ Booking detail operational: customer name, phone, event date/type, guest count, add-ons, notes (both roles)
- ✅ Booking detail financial: line items, derived total, payment history, derived paid/due, add line item, add payment (owner only)
- ✅ Per-event expenses: label, amount, date, category (owner only)
- ✅ WhatsApp confirmation: pre-filled `wa.me` link with all booking details (owner only)
- ✅ Overhead expenses: monthly list + add form (owner only)
- ✅ P&L: income, event expenses, overhead, net profit by month (owner only)
- ✅ Atomic booking creation via Postgres function

**Placeholder scan:** None — all code blocks complete.

**Type consistency:**
- `db/fetch-role` returns a string `"owner"` or `"staff"` — matched in all role checks
- `db/create-booking` calls `create_booking_with_items` RPC — matched in `supabase/create_booking_fn.sql`
- `db/fetch-pnl` returns bookings with nested `:booking_line_items` and `:booking_expenses` — consumed correctly in `pnl.cljs`
- `state/screen` keywords: `:login :inquiries :bookings :booking-detail :new-booking :overhead :pnl` — all handled in `shell.cljs` case
