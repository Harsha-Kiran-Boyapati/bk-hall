(ns bk.public.amenities)

(def amenities-list
  [{:icon "🚗" :title "Ample Parking" :desc "Spacious parking for all your guests"}
   {:icon "🍽️" :title "Catering Kitchen" :desc "Fully equipped kitchen for your caterers"}
   {:icon "🌸" :title "Decoration" :desc "Professional decoration services available"}
   {:icon "👥" :title "Large Capacity" :desc "Comfortably accommodates up to 500 guests"}])

(defn section []
  [:section#amenities {:style {:background "var(--cream)"}}
   [:div.container
    [:h2.section-title "What We Offer"]
    [:p.section-subtitle "Everything you need for an unforgettable event"]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
                   :gap "24px"}}
     (for [{:keys [icon title desc]} amenities-list]
       ^{:key title}
       [:div {:style {:background "#fff"
                      :border-radius "var(--radius)"
                      :padding "32px 24px"
                      :text-align "center"
                      :box-shadow "var(--shadow)"}}
        [:div {:style {:font-size "3rem" :margin-bottom "16px"}} icon]
        [:h3 {:style {:font-size "1.1rem" :font-weight "700" :margin-bottom "8px" :color "var(--dark)"}} title]
        [:p {:style {:color "var(--text-light)" :font-size "0.95rem"}} desc]])]]])
