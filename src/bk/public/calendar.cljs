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
          day]))]))

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
