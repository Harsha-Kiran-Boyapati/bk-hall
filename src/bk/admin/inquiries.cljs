(ns bk.admin.inquiries
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(def status-options ["new" "called back" "converted" "not interested"])

(defn inquiry-row [inq on-status-change]
  (let [{:keys [id name phone event_date event_type guest_count addons message status]} inq
        status-colors {"new" "#3b82f6" "called back" "#f59e0b"
                       "converted" "#16a34a" "not interested" "#9ca3af"}]
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
     [:td {:style {:padding "14px 12px" :color "var(--text-light)" :font-size "13px"
                   :max-width "200px"}} message]
     [:td {:style {:padding "14px 12px"}}
      [:select {:value status
                :on-change #(on-status-change id (.. % -target -value))
                :style {:border "1px solid var(--cream-dark)" :border-radius "6px"
                        :padding "4px 8px" :font-size "13px" :cursor "pointer"
                        :color (get status-colors status "#9ca3af")}}
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
         [:div {:style {:background "#fff" :border-radius "var(--radius)" :overflow "auto"
                        :box-shadow "var(--shadow)"}}
          [:table {:style {:width "100%" :border-collapse "collapse"}}
           [:thead {:style {:background "var(--cream)"}}
            [:tr
             (for [h (cond-> ["Customer" "Date / Type" "Guests" "Add-ons" "Message" "Status"]
                       (= @state/role "owner") (conj "Action"))]
               ^{:key h}
               [:th {:style {:padding "12px" :text-align "left" :font-size "13px"
                             :font-weight "700" :color "var(--text-light)"}} h])]]
           [:tbody
            (for [inq @inquiries]
              ^{:key (:id inq)}
              [inquiry-row inq
               (fn [id status]
                 (db/update-inquiry-status id status
                   (fn [{:keys [ok]}]
                     (when ok
                       (swap! inquiries
                              #(mapv (fn [i] (if (= (:id i) id) (assoc i :status status) i))
                                     %))))))])]]])])))
