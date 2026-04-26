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
       [:div {:style {:display "flex" :justify-content "space-between" :align-items "center"
                      :margin-bottom "24px"}}
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
               [:td {:style {:padding "14px 12px" :color "var(--text-light)"}}
                (str (:guest_count b) " guests")]
               [:td {:style {:padding "14px 12px"}}
                [:span {:style {:background (get status-colors (:status b) "#9ca3af")
                                :color "#fff" :padding "2px 10px" :border-radius "50px"
                                :font-size "12px" :font-weight "600"}}
                 (:status b)]]])]]])])))
