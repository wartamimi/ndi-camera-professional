#include "ndi/ndi_finder.h"

#include <Processing.NDI.Lib.h>

#include <chrono>

namespace ndicam {

std::vector<std::string> find_sources(int timeout_ms, const char* extra_ips) {
    std::vector<std::string> names;
    NDIlib_find_create_t desc;
    desc.show_local_sources = true;
    desc.p_groups = nullptr;
    desc.p_extra_ips = (extra_ips && extra_ips[0]) ? extra_ips : nullptr;
    NDIlib_find_instance_t finder = NDIlib_find_create_v2(&desc);
    if (!finder) return names;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
    while (std::chrono::steady_clock::now() < deadline) {
        const auto left = std::chrono::duration_cast<std::chrono::milliseconds>(deadline - std::chrono::steady_clock::now()).count();
        if (left <= 0) break;
        NDIlib_find_wait_for_sources(finder, static_cast<uint32_t>(left < 500 ? left : 500));
    }
    uint32_t count = 0;
    const NDIlib_source_t* sources = NDIlib_find_get_current_sources(finder, &count);
    for (uint32_t i = 0; i < count; ++i) {
        if (sources[i].p_ndi_name) names.emplace_back(sources[i].p_ndi_name);
    }
    NDIlib_find_destroy(finder);
    return names;
}

}  // namespace ndicam
