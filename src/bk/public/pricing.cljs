(ns bk.public.pricing)

(def services
  ["Hall Hire" "Stage Setup" "Helpers" "Other Add-ons"])

(defn section []
  [:section#pricing {:style {:background "#fff"}}
   [:div.container {:style {:max-width "700px" :text-align "center"}}
    [:h2.section-title "Pricing"]
    [:p.section-subtitle "We offer customised packages tailored to your event"]
    [:div {:style {:display "flex" :flex-wrap "wrap" :gap "12px" :justify-content "center" :margin-bottom "40px"}}
     (for [s services]
       ^{:key s}
       [:span {:style {:background "var(--cream)" :border "1.5px solid var(--cream-dark)"
                       :border-radius "50px" :padding "8px 20px"
                       :font-size "0.95rem" :color "var(--text)"}} s])]
    [:p {:style {:color "var(--text-light)" :margin-bottom "32px" :line-height "1.8"}}
     "Pricing depends on your event date, guest count, and the services you need. "
     "We'll discuss everything on a quick call and give you a clear quote."]
    [:a {:href "#inquiry" :class "btn-primary"} "Get a Quote"]]])
