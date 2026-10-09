(ns bk.public.location)

(defn section []
  [:section#location {:style {:background "var(--dark)" :color "#fff" :border-top "1px solid rgba(255,255,255,0.15)"}}
   [:div.container
    [:h2.section-title {:style {:color "#fff"}} "Find Us"]
    [:p.section-subtitle {:style {:color "rgba(255,255,255,0.7)"}} "BK Function Hall — easy to reach, easy to find"]
    [:div {:style {:display "grid" :grid-template-columns "repeat(auto-fit, minmax(280px, 1fr))" :gap "40px" :align-items "center"}}
     [:div
      [:p {:style {:font-size "1rem" :color "#fff" :line-height "1.8" :margin-bottom "0"}}
       "BK Function Hall" [:br]
       "Tadipatri - Dharmavaram Rd" [:br]
       "Bathalapalli, Andhra Pradesh 515661" [:br]
       [:span {:style {:color "rgba(255,255,255,0.7)" :font-size "0.9rem"}} "Near: Indraamma Colony"]
       [:br]
       [:a {:href "tel:9908323309" :style {:color "#fff" :font-weight "600" :text-decoration "none"}}
        "📞 9908323309"]]]
     [:div {:style {:border-radius "var(--radius)" :overflow "hidden" :height "300px"}}
      [:iframe {:src "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d2000!2d77.779173!3d14.5306815!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x3bb1510032c14a73%3A0x6cda236b0f44c696!2sBK%20Function%20Hall!5e0!3m2!1sen!2sin!4v1"
                :width "100%"
                :height "300"
                :style {:border "0"}
                :allow-full-screen ""
                :loading "lazy"
                :referrer-policy "no-referrer-when-downgrade"}]]]]])
