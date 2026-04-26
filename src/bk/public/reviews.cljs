(ns bk.public.reviews)

(def google-maps-url "https://maps.app.goo.gl/quTKKkZQ2EFVrt1t7")

(defn section []
  [:section#reviews {:style {:background "var(--cream)" :text-align "center"}}
   [:div.container
    [:h2.section-title "What Our Clients Say"]
    [:p.section-subtitle "Read verified reviews from our happy customers"]
    [:a {:href google-maps-url
         :target "_blank"
         :rel "noopener noreferrer"
         :class "btn-primary"
         :style {:display "inline-flex" :align-items "center" :gap "8px"}}
     "⭐ See our Google Reviews"]]])
