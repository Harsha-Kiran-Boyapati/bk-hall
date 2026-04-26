(ns bk.supabase
  (:require ["@supabase/supabase-js" :refer [createClient]]))

(goog-define SUPABASE_URL "")
(goog-define SUPABASE_KEY "")

(defonce client (createClient SUPABASE_URL SUPABASE_KEY))

;; ── Public website ──────────────────────────────────────────────────────────

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

;; ── Auth ────────────────────────────────────────────────────────────────────

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
  (.onAuthStateChange (.-auth client) (fn [_event session] (callback session))))

;; ── Profile ─────────────────────────────────────────────────────────────────

(defn fetch-role [callback]
  (-> (.from client "profiles")
      (.select "role")
      (.eq "user_id" (.. client -auth -currentUser -id))
      (.single)
      (.then #(callback (:role (js->clj (.-data %) :keywordize-keys true))))
      (.catch #(callback nil))))

;; ── Inquiries ────────────────────────────────────────────────────────────────

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

;; ── Bookings ─────────────────────────────────────────────────────────────────

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

(defn create-booking [booking line-items first-payment callback]
  (-> (.rpc client "create_booking_with_items"
            (clj->js {:p_booking booking
                      :p_line_items line-items
                      :p_payment first-payment}))
      (.then #(if (.-error %)
                (callback {:ok false :error (.. % -error -message)})
                (callback {:ok true :id (.-data %)})))
      (.catch #(callback {:ok false :error (.-message %)}))))

;; ── Line items ───────────────────────────────────────────────────────────────

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

;; ── Payments ─────────────────────────────────────────────────────────────────

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

;; ── Booking expenses ─────────────────────────────────────────────────────────

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

;; ── Overhead expenses ────────────────────────────────────────────────────────

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

;; ── P&L ──────────────────────────────────────────────────────────────────────

(defn fetch-pnl [month year callback]
  (let [month-str (-> month str (.padStart 2 "0"))
        date-from (str year "-" month-str "-01")
        date-to   (str year "-" month-str "-31")]
    (-> (.from client "bookings")
        (.select "id,customer_name,event_date,booking_line_items(amount),booking_expenses(amount)")
        (.eq "status" "completed")
        (.gte "event_date" date-from)
        (.lte "event_date" date-to)
        (.then #(callback (js->clj (.-data %) :keywordize-keys true)))
        (.catch #(js/console.error "fetch-pnl error" %)))))
