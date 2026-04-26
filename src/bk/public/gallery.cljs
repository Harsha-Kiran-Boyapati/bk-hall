(ns bk.public.gallery)

(def photos
  [{:src "/images/gallery/hall-1.jpg" :alt "BK Function Hall main hall"}
   {:src "/images/gallery/hall-2.jpg" :alt "BK Function Hall decorated for wedding"}
   {:src "/images/gallery/hall-3.jpg" :alt "BK Function Hall entrance"}
   {:src "/images/gallery/hall-4.jpg" :alt "BK Function Hall stage area"}
   {:src "/images/gallery/hall-5.jpg" :alt "BK Function Hall dining setup"}
   {:src "/images/gallery/hall-6.jpg" :alt "BK Function Hall parking"}])

(defn section []
  [:section#gallery {:style {:background "#fff"}}
   [:div.container
    [:h2.section-title "Our Venue"]
    [:p.section-subtitle "A glimpse of BK Function Hall"]
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(auto-fill, minmax(280px, 1fr))"
                   :gap "16px"}}
     (for [{:keys [src alt]} photos]
       ^{:key src}
       [:div {:style {:border-radius "var(--radius)"
                      :overflow "hidden"
                      :aspect-ratio "4/3"
                      :background "var(--cream-dark)"}}
        [:img {:src src :alt alt
               :style {:width "100%" :height "100%" :object-fit "cover"}}]])]]])
