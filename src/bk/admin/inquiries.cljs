(ns bk.admin.inquiries
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(def status-options ["new" "called back" "converted" "not interested"])
(def event-types ["wedding" "reception" "birthday" "other"])
(def addon-options ["Decoration" "Catering" "Stage Setup" "Photography" "DJ / Music"])

(defn add-inquiry-form [on-added]
  (let [form    (r/atom {:name "" :phone "" :event_date "" :event_type "wedding"
                         :guest_count "" :addons [] :message ""})
        saving? (r/atom false)
        error   (r/atom nil)]
    (fn []
      [:div {:style {:background "var(--cream)" :border-radius "var(--radius)" :padding "24px"
                     :margin-bottom "24px" :border "1.5px solid var(--cream-dark)"}}
       [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "New Inquiry"]
       [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "12px"}}
        [:div.form-group
         [:label.form-label "Name *"]
         [:input.form-input {:value (:name @form)
                             :on-change #(swap! form assoc :name (.. % -target -value))}]]
        [:div.form-group
         [:label.form-label "Phone *"]
         [:input.form-input {:value (:phone @form)
                             :on-change #(swap! form assoc :phone (.. % -target -value))}]]
        [:div.form-group
         [:label.form-label "Event Date *"]
         [:input.form-input {:type "date" :value (:event_date @form)
                             :on-change #(swap! form assoc :event_date (.. % -target -value))}]]
        [:div.form-group
         [:label.form-label "Event Type"]
         [:select.form-input {:value (:event_type @form)
                              :on-change #(swap! form assoc :event_type (.. % -target -value))}
          (for [t event-types] ^{:key t} [:option {:value t} t])]]
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
             [:label {:style {:display "flex" :align-items "center" :gap "6px"
                              :cursor "pointer" :font-size "14px"}}
              [:input {:type "checkbox" :checked selected?
                       :on-change #(swap! form update :addons
                                          (fn [a]
                                            (if selected?
                                              (filterv (fn [x] (not= x addon)) a)
                                              (conj a addon))))}]
              addon]))]]
       [:div.form-group
        [:label.form-label "Message"]
        [:textarea.form-input {:rows 2 :value (:message @form)
                               :on-change #(swap! form assoc :message (.. % -target -value))}]]
       (when @error [:p {:style {:color "#dc2626" :margin-bottom "8px"}} @error])
       [:div {:style {:display "flex" :gap "12px"}}
        [:button {:class "btn-primary" :style {:padding "10px 24px"}
                  :disabled @saving?
                  :on-click (fn []
                              (reset! saving? true)
                              (reset! error nil)
                              (db/submit-inquiry
                                (-> @form
                                    (update :guest_count #(when (seq %) (js/parseInt %))))
                                (fn [{:keys [ok]}]
                                  (reset! saving? false)
                                  (if ok
                                    (do (on-added)
                                        (reset! form {:name "" :phone "" :event_date ""
                                                      :event_type "wedding" :guest_count ""
                                                      :addons [] :message ""}))
                                    (reset! error "Failed to save. Please try again.")))))}
         (if @saving? "Saving…" "Save Inquiry")]]])))

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
  (let [inquiries  (r/atom [])
        loading?   (r/atom true)
        show-form? (r/atom false)
        load       (fn []
                     (reset! loading? true)
                     (db/fetch-inquiries (fn [data]
                                           (reset! inquiries data)
                                           (reset! loading? false))))]
    (load)
    (fn []
      [:div
       [:div {:style {:display "flex" :justify-content "space-between" :align-items "center"
                      :margin-bottom "24px"}}
        [:h2 {:style {:font-size "1.4rem" :font-weight "700"}} "Inquiries"]
        [:button {:on-click #(swap! show-form? not)
                  :class "btn-primary"
                  :style {:padding "10px 20px" :font-size "14px"}}
         (if @show-form? "Cancel" "+ New Inquiry")]]
       (when @show-form?
         [add-inquiry-form (fn []
                             (reset! show-form? false)
                             (load))])
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
