// Icon names an admin can give a badge (profile-service AdminBadgeController.ICONS) mapped to lucide icons.
import {
    Apple, Award, Beef, Bike, CalendarCheck, Crown, Droplet, Dumbbell, Flame, Footprints, Heart, Medal,
    Mountain, Rocket, Scale, Shield, Sparkles, Star, Target, Timer, Trophy, Utensils, Weight, Zap,
} from 'lucide-react';

export const BADGE_ICONS = {
    dumbbell: Dumbbell, 'calendar-check': CalendarCheck, medal: Medal, award: Award, flame: Flame, trophy: Trophy,
    weight: Weight, zap: Zap, scale: Scale, utensils: Utensils, beef: Beef, star: Star, crown: Crown, target: Target,
    heart: Heart, mountain: Mountain, rocket: Rocket, timer: Timer, footprints: Footprints, bike: Bike, apple: Apple,
    droplet: Droplet, shield: Shield, sparkles: Sparkles,
};

export const badgeIcon = (name) => BADGE_ICONS[name] || Award;

/** The badge's text in the UI language: the admin's Arabic text when set, else English. */
export const badgeText = (badge, lang) => ({
    name: (lang === 'ar' && badge.nameAr) || badge.name,
    description: (lang === 'ar' && badge.descriptionAr) || badge.description || '',
});
