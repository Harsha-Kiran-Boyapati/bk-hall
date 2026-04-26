(ns bk.public.inquiry
  (:require [reagent.core :as r]
            [bk.supabase :as db]))

(def addon-options
  ["Decoration" "Catering" "Stage Setup" "Photography" "DJ / Music"])

(defn section []
  (let [form (r/atom {:name "" :phone "" :event_date "" :event_type "wedding"
                      :guest_count "" :addons [] :message ""})
        submitted? (r/atom false)
        error (r/atom nil)
        submitting? (r/atom false)]
    (fn []
      [:section#inquiry {:style {:background "var(--dark)" :color "#fff"}}
       [:div.container {:style {:max-width "700px"}}
        [:h2.section-title {:style {:color "#fff"}} "Make an Inquiry"]
        [:p.section-subtitle {:style {:color "rgba(255,255,255,0.7)"}}
         "Fill in your details and we'll call you back to discuss your requirements"]
        (if @submitted?
          [:div {:style {:text-align "center" :padding "40px"}}
           [:div {:style {:font-size "3rem" :margin-bottom "16px"}} "✅"]
           [:h3 {:style {:font-size "1.4rem" :margin-bottom "8px"}} "Thank you!"]
           [:p {:style {:color "rgba(255,255,255,0.7)"}} "We'll call you shortly to discuss your requirements."]]
          [:form {:on-submit (fn [e]
                               (.preventDefault e)
                               (reset! error nil)
                               (reset! submitting? true)
                               (let [data (-> @form
                                             (update :guest_count #(when (seq %) (js/parseInt %))))]
                                 (db/submit-inquiry data
                                   (fn [{:keys [ok]}]
                                     (reset! submitting? false)
                                     (if ok
                                       (reset! submitted? true)
                                       (reset! error "Something went wrong. Please try again."))))))}
           [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Your Name *"]
             [:input.form-input {:type "text" :required true :placeholder "Ravi Kumar"
                                 :value (:name @form)
                                 :on-change #(swap! form assoc :name (.. % -target -value))}]]
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Phone Number *"]
             [:input.form-input {:type "tel" :required true :placeholder "+91 98765 43210"
                                 :value (:phone @form)
                                 :on-change #(swap! form assoc :phone (.. % -target -value))}]]]
           [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "16px"}}
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Event Date *"]
             [:input.form-input {:type "date" :required true
                                 :value (:event_date @form)
                                 :on-change #(swap! form assoc :event_date (.. % -target -value))}]]
            [:div.form-group
             [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Event Type *"]
             [:select.form-input {:value (:event_type @form)
                                  :on-change #(swap! form assoc :event_type (.. % -target -value))}
              [:option {:value "wedding"} "Wedding"]
              [:option {:value "reception"} "Reception"]
              [:option {:value "birthday"} "Birthday"]
              [:option {:value "other"} "Other"]]]]
           [:div.form-group
            [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Approximate Guest Count"]
            [:input.form-input {:type "number" :placeholder "200"
                                :value (:guest_count @form)
                                :on-change #(swap! form assoc :guest_count (.. % -target -value))}]]
           [:div.form-group
            [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Add-ons Interested In"]
            [:div {:style {:display "flex" :flex-wrap "wrap" :gap "10px" :margin-top "8px"}}
             (for [addon addon-options]
               ^{:key addon}
               (let [selected? (contains? (set (:addons @form)) addon)]
                 [:label {:style {:display "flex" :align-items "center" :gap "6px" :cursor "pointer"
                                  :color "rgba(255,255,255,0.8)" :font-size "14px"}}
                  [:input {:type "checkbox" :checked selected?
                           :on-change #(swap! form update :addons
                                              (fn [addons]
                                                (if selected?
                                                  (filterv (fn [a] (not= a addon)) addons)
                                                  (conj addons addon))))}]
                  addon]))]]
           [:div.form-group
            [:label.form-label {:style {:color "rgba(255,255,255,0.8)"}} "Message"]
            [:textarea.form-input {:rows 4 :placeholder "Any specific requirements or questions..."
                                   :value (:message @form)
                                   :on-change #(swap! form assoc :message (.. % -target -value))}]]
           (when @error
             [:p {:style {:color "#fca5a5" :margin-bottom "16px"}} @error])
           [:button {:type "submit" :class "btn-primary" :disabled @submitting?}
            (if @submitting? "Sending..." "Send Inquiry")]])]])))
