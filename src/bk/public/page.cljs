(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]
            [bk.public.pricing :as pricing]
            [bk.public.calendar :as calendar]
            [bk.public.reviews :as reviews]
            [bk.public.faq :as faq]
            [bk.public.location :as location]
            [bk.public.inquiry :as inquiry]))

(defn footer []
  [:footer {:style {:background "var(--dark)" :color "rgba(255,255,255,0.6)"
                    :text-align "center" :padding "32px 24px" :font-size "14px"}}
   [:p {:style {:margin-bottom "8px" :font-weight "600" :color "#fff"}} "BK Function Hall"]
   [:p "© 2026 BK Function Hall. All rights reserved."]])

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]
   [pricing/section]
   [calendar/section]
   [reviews/section]
   [faq/section]
   [location/section]
   [inquiry/section]
   [footer]])
