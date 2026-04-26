(ns bk.supabase
  (:require ["@supabase/supabase-js" :refer [createClient]]))

;; Replace with actual values from Supabase dashboard (Settings → API)
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
