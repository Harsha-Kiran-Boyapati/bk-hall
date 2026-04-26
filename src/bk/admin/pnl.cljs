(ns bk.admin.pnl
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(def month-names ["January" "February" "March" "April" "May" "June"
                  "July" "August" "September" "October" "November" "December"])

(defn page []
  (let [now      (js/Date.)
        month    (r/atom (inc (.getMonth now)))
        year     (r/atom (.getFullYear now))
        bookings (r/atom [])
        overhead (r/atom [])
        loading? (r/atom false)
        load     (fn []
                   (reset! loading? true)
                   (db/fetch-pnl @month @year
                     (fn [bks]
                       (reset! bookings bks)
                       (db/fetch-overhead @month @year
                         (fn [oh]
                           (reset! overhead oh)
                           (reset! loading? false))))))]
    (load)
    (fn []
      (let [income          (->> @bookings
                                 (mapcat :booking_line_items)
                                 (reduce #(+ % (:amount %2)) 0))
            event-expenses  (->> @bookings
                                 (mapcat :booking_expenses)
                                 (reduce #(+ % (:amount %2)) 0))
            overhead-total  (reduce #(+ % (:amount %2)) 0 @overhead)
            net             (- income event-expenses overhead-total)]
        [:div {:style {:max-width "760px"}}
         [:h2 {:style {:font-size "1.4rem" :font-weight "700" :margin-bottom "24px"}} "Monthly P&L"]

         [:div {:style {:display "flex" :gap "12px" :align-items "center" :margin-bottom "24px"}}
          [:select.form-input {:style {:width "160px"} :value @month
                               :on-change #(do (reset! month (js/parseInt (.. % -target -value)))
                                               (load))}
           (map-indexed (fn [i m] ^{:key i} [:option {:value (inc i)} m]) month-names)]
          [:input.form-input {:type "number" :style {:width "100px"} :value @year
                              :on-change #(do (reset! year (js/parseInt (.. % -target -value)))
                                              (load))}]]

         (if @loading?
           [:p "Loading…"]
           [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "32px"
                          :box-shadow "var(--shadow)"}}
            [:h3 {:style {:font-weight "700" :margin-bottom "16px" :color "var(--text-light)"
                          :font-size "0.9rem" :text-transform "uppercase" :letter-spacing "1px"}}
             (str (nth month-names (dec @month)) " " @year)]

            (for [[label amount color]
                  [["Income (completed bookings)"   income          "#16a34a"]
                   ["Event Expenses"                event-expenses  "#dc2626"]
                   ["Overhead Expenses"             overhead-total  "#dc2626"]]]
              ^{:key label}
              [:div {:style {:display "flex" :justify-content "space-between" :padding "14px 0"
                             :border-bottom "1px solid var(--cream-dark)"}}
               [:span label]
               [:span {:style {:font-weight "600" :color color}}
                (str "₹" (.toLocaleString amount "en-IN"))]])

            [:div {:style {:display "flex" :justify-content "space-between" :padding "16px 0"
                           :font-size "1.15rem" :font-weight "800"
                           :color (if (neg? net) "#dc2626" "#16a34a")}}
             [:span "Net Profit"]
             [:span (str (when (neg? net) "−")
                         "₹" (.toLocaleString (js/Math.abs net) "en-IN"))]]

            (when (seq @bookings)
              [:div {:style {:margin-top "24px"}}
               [:h4 {:style {:font-weight "700" :margin-bottom "12px" :font-size "0.9rem"
                             :color "var(--text-light)" :text-transform "uppercase"}}
                "Events This Month"]
               (for [b @bookings]
                 ^{:key (:id b)}
                 [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                                :border-bottom "1px solid var(--cream-dark)" :font-size "14px"}}
                  [:span (str (:event_date b) " — " (:customer_name b))]
                  [:span {:style {:color "var(--text-light)"}}
                   (str "₹" (.toLocaleString
                              (reduce #(+ % (:amount %2)) 0 (:booking_line_items b))
                              "en-IN"))]])])])]))))
