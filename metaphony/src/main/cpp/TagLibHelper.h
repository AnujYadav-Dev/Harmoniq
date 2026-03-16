//
// Created by Anuj Yadav
//

#ifndef HARMONIQ_TAGLIBHELPER_H
#define HARMONIQ_TAGLIBHELPER_H

#include "audioproperties.h"
#include "tfile.h"

namespace TagLibHelper {
    TagLib::File *detectParser(
            TagLib::FileName filename,
            TagLib::IOStream *stream,
            bool readAudioProperties,
            TagLib::AudioProperties::ReadStyle audioPropertiesStyle);

    TagLib::File *detectByExtension(
            TagLib::FileName filename,
            TagLib::IOStream *stream,
            bool readAudioProperties,
            TagLib::AudioProperties::ReadStyle audioPropertiesStyle);

    TagLib::File *detectByContent(
            TagLib::IOStream *stream,
            bool readAudioProperties,
            TagLib::AudioProperties::ReadStyle audioPropertiesStyle);
}

#endif //HARMONIQ_TAGLIBHELPER_H
