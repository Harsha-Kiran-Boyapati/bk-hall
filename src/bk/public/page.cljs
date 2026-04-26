(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]
            [bk.public.amenities :as amenities]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]
   [amenities/section]])
