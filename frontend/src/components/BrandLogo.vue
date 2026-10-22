<!--
  The BallotBox logo: a ballot with a curled corner, a plus, and a radio tower
  broadcasting (drawn 2026-10-22; source in assets/logo/). Lines use
  currentColor, so it takes the text colour around it; only the beacon is red.
  Decorative: the "BallotBox" name sits next to it, so screen readers skip it.
-->
<template>
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 140 70" aria-hidden="true" focusable="false" class="block h-full w-auto">
    <!-- Cartoon sketch: a ballot with a curled corner, a plus, and a radio tower broadcasting.
         Lines use currentColor, so the logo takes the text colour of wherever it sits. -->
    <defs>
      <!-- A slight wobble so the lines look hand-drawn. -->
      <filter id="brand-logo-sketch" x="-5%" y="-5%" width="110%" height="110%">
        <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4" result="noise"/>
        <feDisplacementMap in="SourceGraphic" in2="noise" scale="1.3"/>
      </filter>
    </defs>
    <g fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" filter="url(#brand-logo-sketch)">
      <!-- the ballot: a page whose top-right corner peels back and rolls toward you -->
      <path d="M29.5 8.6 L8 9 L8.5 63.5 L46.5 63 L46.3 24.5"/>
      <!-- the rolled-back flap: the back of the page shows, tinted; its tip curls under -->
      <path d="M29.5 8.6 C36 7.2 43.5 10.5 46.3 24.5 C42 21.2 38.6 21.4 36.4 23.6 C33.2 26.6 30.4 22.8 33.4 20.4 C35.6 18.6 34.4 14.2 29.5 8.6 Z"
            fill="currentColor" fill-opacity="0.18" stroke-width="1.9"/>
      <!-- a shading stroke along the roll -->
      <path d="M32.4 10.6 C38.2 11.4 42 15.4 44 21.2" stroke-width="1.1" opacity="0.65"/>
      <!-- "Ballot", handwritten -->
      <g stroke-width="1.7">
        <path d="M12 28 L12 41 M12 28 Q18.5 27.5 18 31.5 Q17.5 34.5 12 34.5 Q19.5 34.5 19 38.5 Q18.5 41.5 12 41"/>
        <path d="M25.2 35.5 Q21.5 33.5 21 38 Q21 42.5 25 40.5 M25.2 34.5 L25.4 41.5"/>
        <path d="M28.5 27.5 L28.6 41.5"/>
        <path d="M31.7 27.5 L31.8 41.5"/>
        <ellipse cx="36.3" cy="38.2" rx="2.6" ry="3.2"/>
        <path d="M41.3 30 L41.4 40.5 Q41.5 42 43.4 41.2 M39.2 33.6 L43.4 33.4"/>
      </g>
      <!-- a couple of ruled lines and a tick box, so it reads as a ballot -->
      <path d="M12.5 48.5 L15.5 48.5 L15.5 51.5 L12.5 51.5 Z M13.2 50 L14.2 51 L16.5 47.3" stroke-width="1.4"/>
      <path d="M19 50 L41 49.6 M12.5 56.5 L41 56.2" stroke-width="1.3" opacity="0.7"/>

      <!-- plus -->
      <path d="M63 28.5 L63.2 43.5 M55.5 36.2 L70.5 35.8" stroke-width="2.6"/>

      <!-- the tower: a tall three-legged tripod with cross-bracing -->
      <path d="M110 19 L91 64 M110 19 L129 64 M110 19 L113 64"/>
      <path d="M102.6 37 L117.6 37 M98.3 47 L121.8 47 M94.6 56 L125.6 56" stroke-width="1.5"/>
      <path d="M106.3 28 L111.5 37 L102.6 37 M111.5 37 L98.3 47 L112.3 47 L121.8 47 M112.3 47 L94.6 56 L125.6 56 L112.3 47" stroke-width="1.2" opacity="0.8"/>
      <path d="M88 64.5 L132 64" stroke-width="1.6"/>
      <!-- the beacon, and its signal going out both ways -->
      <path d="M110 14 L110 19" stroke-width="1.8"/>
      <g stroke-width="1.8">
        <path d="M115.4 9.5 A7 7 0 0 1 115.4 18.5"/>
        <path d="M119.2 6.3 A12 12 0 0 1 119.2 21.7"/>
        <path d="M123 3.1 A17 17 0 0 1 123 24.9"/>
        <path d="M104.6 9.5 A7 7 0 0 0 104.6 18.5"/>
        <path d="M100.8 6.3 A12 12 0 0 0 100.8 21.7"/>
        <path d="M97 3.1 A17 17 0 0 0 97 24.9"/>
      </g>
    </g>
    <circle cx="110" cy="13" r="3.6" fill="#f87171" stroke="currentColor" stroke-width="1.6" filter="url(#brand-logo-sketch)"/>
  </svg>
</template>
