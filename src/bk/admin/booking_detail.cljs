(ns bk.admin.booking-detail
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(defn fmt-currency [n]
  (str "₹" (.toLocaleString (or n 0) "en-IN")))

(defn whatsapp-message [booking line-items payments]
  (let [total (reduce + 0 (map :amount line-items))
        paid  (reduce + 0 (map :amount payments))
        due   (- total paid)
        lines (clojure.string/join "\n"
                (map #(str "  " (:label %) ": " (fmt-currency (:amount %))) line-items))]
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

(defn line-item-row [item on-refresh]
  (let [editing? (r/atom false)
        label    (r/atom (:label item))
        amount   (r/atom (str (:amount item)))
        saving?  (r/atom false)]
    (fn [item on-refresh]
      [:div {:style {:display "flex" :align-items "center" :gap "8px"
                     :padding "8px 0" :border-bottom "1px solid var(--cream-dark)"}}
       (if @editing?
         [:<>
          [:input.form-input {:value @label :style {:flex "2"}
                              :on-change #(reset! label (.. % -target -value))}]
          [:input.form-input {:type "number" :value @amount :style {:flex "1"}
                              :on-change #(reset! amount (.. % -target -value))}]
          [:button {:on-click (fn []
                                (reset! saving? true)
                                (db/update-line-item (:id item) @label (js/parseFloat @amount)
                                  (fn [{:keys [ok]}]
                                    (reset! saving? false)
                                    (when ok
                                      (reset! editing? false)
                                      (on-refresh)))))
                    :disabled @saving?
                    :style {:background "var(--gold)" :color "#fff" :border "none"
                            :padding "6px 12px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px" :white-space "nowrap"}}
           "Save"]
          [:button {:on-click #(reset! editing? false)
                    :style {:background "none" :border "1px solid var(--cream-dark)"
                            :padding "6px 10px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px"}}
           "Cancel"]]
         [:<>
          [:span {:style {:flex "2"}} (:label item)]
          [:span {:style {:font-weight "600" :flex "1" :text-align "right"}}
           (fmt-currency (:amount item))]
          [:button {:on-click #(reset! editing? true)
                    :style {:background "none" :border "1px solid var(--cream-dark)"
                            :padding "4px 10px" :border-radius "6px" :cursor "pointer"
                            :font-size "12px" :color "var(--text-light)"}}
           "Edit"]
          [:button {:on-click (fn []
                                (db/delete-line-item (:id item)
                                  (fn [{:keys [ok]}] (when ok (on-refresh)))))
                    :style {:background "none" :border "none" :color "#dc2626"
                            :font-size "18px" :cursor "pointer" :line-height "1"
                            :padding "0 4px"}}
           "×"]])])))

(defn add-line-item-form [booking-id on-added]
  (let [form    (r/atom {:label "" :amount ""})
        saving? (r/atom false)]
    (fn []
      [:div {:style {:display "flex" :gap "12px" :margin-top "16px" :align-items "flex-end"
                     :border-top "1px solid var(--cream-dark)" :padding-top "16px"}}
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
                             (db/add-line-item
                               {:booking_id booking-id
                                :label (:label @form)
                                :amount (js/parseFloat (:amount @form))}
                               (fn [{:keys [ok]}]
                                 (reset! saving? false)
                                 (when ok
                                   (on-added)
                                   (reset! form {:label "" :amount ""})))))}
        "Add"]])))

(defn payment-row [pmt on-refresh]
  (let [editing? (r/atom false)
        amount   (r/atom (str (:amount pmt)))
        date     (r/atom (:date pmt))
        note     (r/atom (or (:note pmt) ""))
        saving?  (r/atom false)]
    (fn [pmt on-refresh]
      [:div {:style {:display "flex" :align-items "center" :gap "8px"
                     :padding "8px 0" :border-bottom "1px solid var(--cream-dark)"}}
       (if @editing?
         [:<>
          [:input.form-input {:type "number" :value @amount :style {:flex "1"}
                              :on-change #(reset! amount (.. % -target -value))}]
          [:input.form-input {:type "date" :value @date :style {:flex "1"}
                              :on-change #(reset! date (.. % -target -value))}]
          [:input.form-input {:value @note :style {:flex "1"} :placeholder "Note"
                              :on-change #(reset! note (.. % -target -value))}]
          [:button {:on-click (fn []
                                (reset! saving? true)
                                (db/update-payment (:id pmt)
                                  {:amount (js/parseFloat @amount) :date @date :note @note}
                                  (fn [{:keys [ok]}]
                                    (reset! saving? false)
                                    (when ok (reset! editing? false) (on-refresh)))))
                    :disabled @saving?
                    :style {:background "var(--gold)" :color "#fff" :border "none"
                            :padding "6px 12px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px" :white-space "nowrap"}}
           "Save"]
          [:button {:on-click #(reset! editing? false)
                    :style {:background "none" :border "1px solid var(--cream-dark)"
                            :padding "6px 10px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px"}}
           "Cancel"]]
         [:<>
          [:span {:style {:flex "2"}}
           (str (:date pmt) (when (seq (:note pmt)) (str " — " (:note pmt))))]
          [:span {:style {:font-weight "600" :color "#16a34a" :flex "1" :text-align "right"}}
           (fmt-currency (:amount pmt))]
          [:button {:on-click #(reset! editing? true)
                    :style {:background "none" :border "1px solid var(--cream-dark)"
                            :padding "4px 10px" :border-radius "6px" :cursor "pointer"
                            :font-size "12px" :color "var(--text-light)"}}
           "Edit"]
          [:button {:on-click #(db/delete-payment (:id pmt)
                                 (fn [{:keys [ok]}] (when ok (on-refresh))))
                    :style {:background "none" :border "none" :color "#dc2626"
                            :font-size "18px" :cursor "pointer" :line-height "1"
                            :padding "0 4px"}}
           "×"]])])))

(defn expense-row [exp on-refresh]
  (let [editing?  (r/atom false)
        label     (r/atom (:label exp))
        amount    (r/atom (str (:amount exp)))
        date      (r/atom (:date exp))
        category  (r/atom (:category exp))
        saving?   (r/atom false)]
    (fn [exp on-refresh]
      [:div {:style {:display "flex" :align-items "center" :gap "8px"
                     :padding "8px 0" :border-bottom "1px solid var(--cream-dark)"}}
       (if @editing?
         [:<>
          [:input.form-input {:value @label :style {:flex "2"}
                              :on-change #(reset! label (.. % -target -value))}]
          [:input.form-input {:type "number" :value @amount :style {:flex "1"}
                              :on-change #(reset! amount (.. % -target -value))}]
          [:input.form-input {:type "date" :value @date :style {:flex "1"}
                              :on-change #(reset! date (.. % -target -value))}]
          [:select.form-input {:value @category :style {:flex "1"}
                               :on-change #(reset! category (.. % -target -value))}
           (for [c ["electricity" "labour" "catering" "decoration" "other"]]
             ^{:key c} [:option {:value c} c])]
          [:button {:on-click (fn []
                                (reset! saving? true)
                                (db/update-booking-expense (:id exp)
                                  {:label @label :amount (js/parseFloat @amount)
                                   :date @date :category @category}
                                  (fn [{:keys [ok]}]
                                    (reset! saving? false)
                                    (when ok (reset! editing? false) (on-refresh)))))
                    :disabled @saving?
                    :style {:background "var(--gold)" :color "#fff" :border "none"
                            :padding "6px 12px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px" :white-space "nowrap"}}
           "Save"]
          [:button {:on-click #(reset! editing? false)
                    :style {:background "none" :border "1px solid var(--cream-dark)"
                            :padding "6px 10px" :border-radius "6px" :cursor "pointer"
                            :font-size "13px"}}
           "Cancel"]]
         [:<>
          [:span {:style {:flex "2"}}
           (str (:label exp) " — " (:date exp) " [" (:category exp) "]")]
          [:span {:style {:font-weight "600" :color "#dc2626" :flex "1" :text-align "right"}}
           (fmt-currency (:amount exp))]
          [:button {:on-click #(reset! editing? true)
                    :style {:background "none" :border "1px solid var(--cream-dark)"
                            :padding "4px 10px" :border-radius "6px" :cursor "pointer"
                            :font-size "12px" :color "var(--text-light)"}}
           "Edit"]
          [:button {:on-click #(db/delete-booking-expense (:id exp)
                                 (fn [{:keys [ok]}] (when ok (on-refresh))))
                    :style {:background "none" :border "none" :color "#dc2626"
                            :font-size "18px" :cursor "pointer" :line-height "1"
                            :padding "0 4px"}}
           "×"]])])))

(defn add-payment-form [booking-id on-added]
  (let [form    (r/atom {:amount "" :date "" :note ""})
        saving? (r/atom false)]
    (fn []
      [:div {:style {:display "flex" :gap "12px" :margin-top "16px" :align-items "flex-end"
                     :border-top "1px solid var(--cream-dark)" :padding-top "16px"}}
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
                             (db/add-payment
                               {:booking_id booking-id
                                :amount (js/parseFloat (:amount @form))
                                :date (:date @form)
                                :note (:note @form)}
                               (fn [{:keys [ok]}]
                                 (reset! saving? false)
                                 (when ok
                                   (on-added)
                                   (reset! form {:amount "" :date "" :note ""})))))}
        "Add"]])))

(defn add-expense-form [booking-id on-added]
  (let [form    (r/atom {:label "" :amount "" :date "" :category "labour"})
        saving? (r/atom false)]
    (fn []
      [:div {:style {:display "grid" :grid-template-columns "2fr 1fr 1fr 1fr auto"
                     :gap "12px" :margin-top "16px" :align-items "flex-end"
                     :border-top "1px solid var(--cream-dark)" :padding-top "16px"}}
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
                             (db/add-booking-expense
                               {:booking_id booking-id
                                :label (:label @form)
                                :amount (js/parseFloat (:amount @form))
                                :date (:date @form)
                                :category (:category @form)}
                               (fn [{:keys [ok]}]
                                 (reset! saving? false)
                                 (when ok
                                   (on-added)
                                   (reset! form {:label "" :amount "" :date "" :category "labour"})))))}
        "Add"]])))

(defn card [title & children]
  (into [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                       :box-shadow "var(--shadow)" :margin-bottom "24px"}}
         [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} title]]
        children))

(defn page []
  (let [booking-id    (get @state/screen-params :booking-id)
        booking       (r/atom nil)
        line-items    (r/atom [])
        payments      (r/atom [])
        expenses      (r/atom [])
        owner?        (= @state/role "owner")
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
              total (reduce + 0 (map :amount @line-items))
              paid  (reduce + 0 (map :amount @payments))
              due   (- total paid)]
          [:div {:style {:max-width "860px"}}
           ;; Header
           [:div {:style {:display "flex" :align-items "center" :gap "16px" :margin-bottom "24px"}}
            [:button {:on-click #(reset! state/screen :bookings)
                      :style {:background "none" :border "none" :color "var(--gold)"
                              :cursor "pointer" :font-size "14px"}} "← Bookings"]
            [:h2 {:style {:font-size "1.4rem" :font-weight "700"}} customer_name]
            [:select {:value status
                      :on-change #(db/update-booking-status booking-id (.. % -target -value)
                                    (fn [{:keys [ok]}]
                                      (when ok
                                        (swap! booking assoc :status (.. % -target -value)))))
                      :style {:border "1px solid var(--cream-dark)" :border-radius "6px"
                              :padding "4px 8px" :font-size "13px" :margin-left "auto"}}
             (for [s ["confirmed" "completed" "cancelled"]]
               ^{:key s} [:option {:value s} s])]]

           ;; Operational details
           [card "Event Details"
            [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
             (for [[label value] [["Phone" phone] ["Date" event_date]
                                  ["Event Type" event_type] ["Guests" (str guest_count)]
                                  ["Add-ons" (clojure.string/join ", " addons)]
                                  ["Notes" (or notes "—")]]]
               ^{:key label}
               [:div
                [:div {:style {:color "var(--text-light)" :font-size "12px" :margin-bottom "4px"}} label]
                [:div value]])]]

           ;; Financial — owner only
           (when owner?
             [:<>
              ;; Line items
              [card "Charges (Line Items)"
               (for [item @line-items]
                 ^{:key (:id item)}
                 [line-item-row item load-financials])
               [:div {:style {:display "flex" :justify-content "space-between"
                              :padding "12px 0" :font-weight "700" :font-size "1.05rem"}}
                [:span "Total"] [:span (fmt-currency total)]]
               [add-line-item-form booking-id load-financials]]

              ;; Payments
              [card "Payments"
               (for [pmt @payments]
                 ^{:key (:id pmt)}
                 [payment-row pmt load-financials])
               [:div {:style {:display "flex" :justify-content "space-between" :padding "10px 0"
                              :border-top "2px solid var(--cream-dark)"}}
                [:span "Paid"] [:span {:style {:color "#16a34a" :font-weight "700"}} (fmt-currency paid)]]
               [:div {:style {:display "flex" :justify-content "space-between" :padding "8px 0"
                              :font-weight "700" :font-size "1.05rem"
                              :color (if (pos? due) "#dc2626" "#16a34a")}}
                [:span "Balance Due"] [:span (fmt-currency due)]]
               [add-payment-form booking-id load-financials]
               [:div {:style {:margin-top "20px" :text-align "right"}}
                [:a {:href (str "https://wa.me/"
                                (clojure.string/replace (or phone "") #"[^0-9]" "")
                                "?text=" (whatsapp-message @booking @line-items @payments))
                     :target "_blank"
                     :class "btn-primary"
                     :style {:display "inline-flex" :align-items "center" :gap "8px"}}
                 "📱 Send WhatsApp Confirmation"]]]

              ;; Per-event expenses
              [card "Event Expenses (What you spent)"
               (for [exp @expenses]
                 ^{:key (:id exp)}
                 [expense-row exp load-financials])
               [add-expense-form booking-id load-financials]]])

           ])))))
