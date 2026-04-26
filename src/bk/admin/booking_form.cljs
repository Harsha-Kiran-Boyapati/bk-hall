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
                     :font-size "20px" :cursor "pointer" :line-height "1" :padding "0 4px"}}
    "×"]])

(defn page []
  (let [prefill (get @state/screen-params :inquiry)
        form (r/atom {:customer_name (or (:name prefill) "")
                      :phone         (or (:phone prefill) "")
                      :event_date    (or (:event_date prefill) "")
                      :event_type    (or (:event_type prefill) "wedding")
                      :guest_count   (or (some-> prefill :guest_count str) "")
                      :addons        (or (:addons prefill) [])
                      :notes         ""})
        line-items (r/atom [{:label "Venue hire" :amount ""}])
        payment    (r/atom {:amount "" :date "" :note "Advance"})
        error      (r/atom nil)
        saving?    (r/atom false)]
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
         [:label.form-label "Notes"]
         [:textarea.form-input {:rows 3 :value (:notes @form)
                                :on-change #(swap! form assoc :notes (.. % -target -value))}]]

        [:hr {:style {:border "none" :border-top "1px solid var(--cream-dark)" :margin "24px 0"}}]
        [:h3 {:style {:font-weight "700" :margin-bottom "16px" :color "var(--dark)"}}
         "Line Items (Charges to customer)"]
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
                                               (update :guest_count
                                                       #(when (seq %) (js/parseInt %))))
                                    items (mapv #(update % :amount js/parseFloat) @line-items)
                                    pmt   (update @payment :amount js/parseFloat)]
                                (db/create-booking booking items pmt
                                  (fn [{:keys [ok] :as result}]
                                    (reset! saving? false)
                                    (if ok
                                      (reset! state/screen :bookings)
                                      (reset! error (or (:error result) "Something went wrong")))))))}
         (if @saving? "Saving…" "Confirm Booking")]]])))
