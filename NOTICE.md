Klinik.Net
Copyright (c) 2026 Klinik.Net

This product includes icon geometry from Lucide (https://lucide.dev),
specifically the "car-front" glyph, used under the ISC License:

    Permission to use, copy, modify, and/or distribute this software for any
    purpose with or without fee is hereby granted, provided that the above
    copyright notice and this permission notice appear in all copies.

    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

Icon components elsewhere in the app are drawn from
https://composables.com/icons/icon-libraries/lucide (MIT).

Bundled typefaces
-----------------

The app bundles two families under app/src/main/res/font/. The prototype loads
them from the Google Fonts CDN at runtime; an offline tablet app cannot, so the
glyphs ship in the APK.

  Plus Jakarta Sans  — SIL Open Font License 1.1
                       https://fonts.google.com/specimen/Plus+Jakarta+Sans
  Fraunces           — SIL Open Font License 1.1
                       https://fonts.google.com/specimen/Fraunces

Both were obtained from the google/fonts repository and instanced to static
cuts at the weights the stylesheet asks for. Variable-font support is API 26+
and this app's minSdk is 24, so a variable file alone would render every weight
as the default instance on API 24-25.

    SIL OPEN FONT LICENSE Version 1.1

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of the font software and associated documentation files (the "Font
    Software"), to use, study, copy, merge, embed, modify, redistribute, and
    sell modified and unmodified copies of the Font Software, subject to the
    following conditions:

    1) Neither the Font Software nor any of its individual components, in
       Original or Modified Versions, may be sold by itself.
    2) Original or Modified Versions of the Font Software may be bundled,
       redistributed and/or sold with any software, provided that each copy
       contains the above copyright notice and this license. These can be
       included either as stand-alone text files, human-readable headers or in
       the appropriate machine-readable metadata fields within text or binary
       files as long as those fields can be easily viewed by the user.
    3) No Modified Version of the Font Software may use the Reserved Font
       Name(s) unless explicit written permission is granted by the copyright
       holder(s).
    4) The name(s) of the copyright holder(s), the author(s) or contributor(s)
       of the Font Software shall not be used to promote, endorse or
       advertise any Modified Version, except to acknowledge the contribution(s)
       of the copyright holder(s), the author(s) or contributor(s) or with
       their explicit written permission.
    5) The Font Software, modified or unmodified, in part or in whole, must be
       distributed entirely under this license, and must not be distributed
       under any other license.