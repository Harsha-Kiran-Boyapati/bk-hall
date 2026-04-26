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
   [inquiry/section]])
