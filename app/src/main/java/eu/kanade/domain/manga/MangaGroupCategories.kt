package eu.kanade.domain.manga

import eu.kanade.domain.merge.GroupCategories
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories

class MangaGroupCategories(getCategories: GetCategories, setMangaCategories: SetMangaCategories) :
    GroupCategories(
        categoriesOf = { id -> getCategories.await(id).map { it.id } },
        write = { id, categoryIds -> setMangaCategories.await(id, categoryIds) },
    )
