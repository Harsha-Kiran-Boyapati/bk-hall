(ns bk.admin.overhead
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(def month-names ["January" "February" "March" "April" "May" "June"
                  "July" "August" "September" "October" "November" "December"])

(defn page []
  (let [now     (js/Date.)
        month   (r/atom (inc (.getMonth now)))
        year    (r/atom (.getFullYear now))
        expenses (r/atom [])
        form    (r/atom {:label "" :amount "" :category "electricity"})
        saving? (r/atom false)
        load    (fn [] (db/fetch-overhead @month @year #(reset! expenses %)))]
    (load)
    (fn []
      [:div {:style {:max-width "760px"}}
       [:h2 {:style {:font-size "1.4rem" :font-weight "700" :margin-bottom "24px"}}
        "Overhead Expenses"]

       [:div {:style {:display "flex" :gap "12px" :align-items "center" :margin-bottom "24px"}}
        [:select.form-input {:style {:width "160px"} :value @month
                             :on-change #(do (reset! month (js/parseInt (.. % -target -value)))
                                             (load))}
         (map-indexed
          (fn [i m] ^{:key i} [:option {:value (inc i)} m])
          month-names)]
        [:input.form-input {:type "number" :style {:width "100px"} :value @year
                            :on-change #(do (reset! year (js/parseInt (.. % -target -value)))
                                            (load))}]]

       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                      :box-shadow "var(--shadow)" :margin-bottom "24px"}}
        (if (empty? @expenses)
          [:p {:style {:color "var(--text-light)"}} "No expenses for this month."]
          [:<>
           (for [exp @expenses]
             ^{:key (:id exp)}
             [:div {:style {:display "flex" :justify-content "space-between" :padding "10px 0"
                            :border-bottom "1px solid var(--cream-dark)"}}
              [:span (str (:label exp) " [" (:category exp) "]")]
              [:span {:style {:font-weight "600" :color "#dc2626"}}
               (str "₹" (.toLocaleString (:amount exp) "en-IN"))]])
           [:div {:style {:display "flex" :justify-content "space-between" :padding "12px 0"
                          :font-weight "700" :border-top "2px solid var(--cream-dark)"}}
            [:span "Total"]
            [:span (str "₹" (.toLocaleString (reduce + 0 (map :amount @expenses)) "en-IN"))]]])]

       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "28px"
                      :box-shadow "var(--shadow)"}}
        [:h3 {:style {:font-weight "700" :margin-bottom "16px"}} "Add Expense"]
        [:div {:style {:display "grid" :grid-template-columns "2fr 1fr 1fr auto"
                       :gap "12px" :align-items "flex-end"}}
         [:div
          [:label.form-label "Label"]
          [:input.form-input {:placeholder "e.g. Electricity bill — April"
                              :value (:label @form)
                              :on-change #(swap! form assoc :label (.. % -target -value))}]]
         [:div
          [:label.form-label "Amount"]
          [:input.form-input {:type "number" :value (:amount @form)
                              :on-change #(swap! form assoc :amount (.. % -target -value))}]]
         [:div
          [:label.form-label "Category"]
          [:select.form-input {:value (:category @form)
                               :on-change #(swap! form assoc :category (.. % -target -value))}
           (for [c ["electricity" "maintenance" "cleaning" "labour" "other"]]
             ^{:key c} [:option {:value c} c])]]
         [:button {:class "btn-primary" :style {:padding "10px 20px"}
                   :disabled @saving?
                   :on-click (fn []
                               (reset! saving? true)
                               (db/add-overhead
                                 {:label    (:label @form)
                                  :amount   (js/parseFloat (:amount @form))
                                  :month    @month
                                  :year     @year
                                  :category (:category @form)}
                                 (fn [{:keys [ok]}]
                                   (reset! saving? false)
                                   (when ok
                                     (load)
                                     (reset! form {:label "" :amount "" :category "electricity"})))))}
          "Add"]]]])))
