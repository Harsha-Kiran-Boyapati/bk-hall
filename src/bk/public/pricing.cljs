(ns bk.public.pricing)

(def pricing-items
  [{:label "Hall Hire" :from "₹30,000" :note "Full day"}
   {:label "Decoration" :from "₹15,000" :note "Starting price, varies by extent"}
   {:label "Catering" :from "₹500/plate" :note "Minimum 100 plates"}
   {:label "Other Add-ons" :from "Custom" :note "Discuss on call"}])

(defn section []
  [:section#pricing {:style {:background "#fff"}}
   [:div.container
    [:h2.section-title "Pricing"]
    [:p.section-subtitle "Starting rates — final pricing confirmed on call based on your requirements"]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
                   :gap "20px"
                   :margin-bottom "40px"}}
     (for [{:keys [label from note]} pricing-items]
       ^{:key label}
       [:div {:style {:border "2px solid var(--cream-dark)"
                      :border-radius "var(--radius)"
                      :padding "28px 24px"
                      :text-align "center"}}
        [:div {:style {:font-size "0.85rem" :font-weight "600" :color "var(--text-light)" :text-transform "uppercase" :letter-spacing "1px" :margin-bottom "8px"}} label]
        [:div {:style {:font-size "1.8rem" :font-weight "800" :color "var(--gold)" :margin-bottom "8px"}} from]
        [:div {:style {:font-size "0.9rem" :color "var(--text-light)"}} note]])]
    [:p {:style {:text-align "center" :color "var(--text-light)" :font-size "0.95rem"}}
     "📞 Call us or "
     [:a {:href "#inquiry" :style {:color "var(--gold)" :font-weight "600"}} "send an inquiry"]
     " to discuss your exact requirements and get a custom quote."]]])
