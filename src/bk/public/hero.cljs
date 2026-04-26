(ns bk.public.hero)

(defn section []
  [:section#hero
   {:style {:background "linear-gradient(rgba(0,0,0,0.5), rgba(0,0,0,0.6)), url('/images/hall-hero.jpg') center/cover no-repeat"
            :min-height "100vh"
            :display "flex"
            :align-items "center"
            :justify-content "center"
            :text-align "center"
            :color "#fff"
            :padding "0 24px"}}
   [:div
    [:h1 {:style {:font-size "clamp(2.5rem, 6vw, 4.5rem)"
                  :font-weight "800"
                  :letter-spacing "-1px"
                  :margin-bottom "16px"}}
     "BK Function Hall"]
    [:p {:style {:font-size "clamp(1.1rem, 2vw, 1.4rem)"
                 :opacity "0.9"
                 :margin-bottom "40px"
                 :max-width "500px"
                 :margin-left "auto"
                 :margin-right "auto"}}
     "The perfect venue for weddings, receptions & celebrations in your city"]
    [:div {:style {:display "flex" :gap "16px" :justify-content "center" :flex-wrap "wrap"}}
     [:a {:href "#calendar" :class "btn-outline"} "Check Availability"]
     [:a {:href "#inquiry" :class "btn-primary"} "Make an Inquiry"]]]])
