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
