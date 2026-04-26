(ns bk.public.location)

(def directions-url "https://maps.app.goo.gl/quTKKkZQ2EFVrt1t7")

(defn section []
  [:section#location {:style {:background "var(--cream)"}}
   [:div.container
    [:h2.section-title "Find Us"]
    [:p.section-subtitle "BK Function Hall — easy to reach, easy to find"]
    [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "40px" :align-items "center"}}
     [:div
      [:p {:style {:font-size "1rem" :color "var(--text)" :line-height "1.8" :margin-bottom "24px"}}
       "BK Function Hall" [:br]
       "Address line 1" [:br]
       "City, State — PIN" [:br]
       [:span {:style {:color "var(--text-light)" :font-size "0.9rem"}} "Near: landmark"]]
      [:a {:href directions-url
           :target "_blank"
           :rel "noopener noreferrer"
           :class "btn-primary"}
       "📍 Get Directions"]]
     [:div {:style {:border-radius "var(--radius)" :overflow "hidden" :height "300px"}}
      [:iframe {:src "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d2000!2d77.779173!3d14.5306815!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x3bb1510032c14a73%3A0x6cda236b0f44c696!2sBK%20Function%20Hall!5e0!3m2!1sen!2sin!4v1"
                :width "100%"
                :height "300"
                :style {:border "0"}
                :allow-full-screen ""
                :loading "lazy"
                :referrer-policy "no-referrer-when-downgrade"}]]]]])
